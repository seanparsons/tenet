package com.github.seanparsons.tenet.netty

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.{HttpRequest => NettyHttpRequest, HttpResponse => NettyHttpResponse, _}
import org.jboss.netty.handler.codec.http.HttpHeaders._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.jboss.netty.bootstrap.ServerBootstrap
import socket.nio.NioServerSocketChannelFactory
import scalaz._
import Scalaz._
import scalaz.effect._
import scalaz.effect.IO._
import scala.collection.JavaConverters._
import java.net.InetSocketAddress
import com.github.seanparsons.tenet._
import java.nio.charset.Charset
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure => TryFailure, Success => TrySuccess}
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import org.jboss.netty.buffer.ChannelBuffers


trait NettyConversions {
  def fromNettyRequest(nettyRequest: NettyHttpRequest): Request
  def toNettyResponse(response: Response): NettyHttpResponse
}

object NettyConversions {
  def createForCharset(charset: Charset = Charset.forName("UTF-8")): NettyConversions = new NettyConversions {
    override def fromNettyRequest(nettyRequest: NettyHttpRequest) = {
      new Request(
        nettyRequest.getUri,
        nettyRequest.getMethod.getName,
        nettyRequest
          .getHeaders
          .asScala
          .toList
          .foldMap(entry => Map(entry.getKey -> List(entry.getValue))),
        nettyRequest.getContent.toString(charset)
      )
    }

    override def toNettyResponse(response: Response) = {
      import Response._
      val nettyResponse = new DefaultHttpResponse(
        HTTP_1_1,
        HttpResponseStatus.valueOf(response.statusCode)
      )
      (defaultHeaders + ("Content-Length" -> List(response.body.length.toString)) ++ response.headers).foldLeft(nettyResponse){(workingResponse, header) =>
        workingResponse.setHeader(header._1, header._2.asJava)
        workingResponse
      }
      nettyResponse.setContent(ChannelBuffers.copiedBuffer(response.body, charset))
      nettyResponse
    }
  }
}

case class HttpServerInitializer(requestHandler: Request => Future[Response])(implicit nettyConversions: NettyConversions, executionContext: ExecutionContext) extends ChannelPipelineFactory {
  override def getPipeline(): ChannelPipeline = {
    // Create a default pipeline implementation.
    val pipeline = Channels.pipeline()

    pipeline.addLast("httpDecoder", new HttpRequestDecoder())
    // Uncomment the following line if you don't want to handle HttpChunks.
    pipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    pipeline.addLast("httpEncoder", new HttpResponseEncoder())
    // Remove the following line if you don't want automatic content compression.
    pipeline.addLast("httpDeflater", new HttpContentCompressor())
    pipeline.addLast("handler", new HttpServerHandler(requestHandler))

    pipeline
  }
}

case class HttpServerHandler(requestHandler: Request => Future[Response])(implicit nettyConversions: NettyConversions, executionContext: ExecutionContext) extends SimpleChannelUpstreamHandler {
  val logger = LoggerFactory.getLogger(getClass)

  override def messageReceived(context: ChannelHandlerContext, messageEvent: MessageEvent) {
    val httpRequest: NettyHttpRequest = messageEvent.getMessage.asInstanceOf[NettyHttpRequest]
    val channel = messageEvent.getChannel

    def handleException(throwable: Throwable) {
      // Log the failure.
      logger.error("Error while processing request.", throwable)

      // Write the response.
      val channelFuture = channel.write(nettyConversions.toNettyResponse(Response(statusCode = 500)))

      // Close the connection by default?
      channelFuture.addListener(ChannelFutureListener.CLOSE)
    }

    // Transform the response.
    try {
      val responseFuture = httpRequest |> nettyConversions.fromNettyRequest |> requestHandler
      responseFuture.onComplete{response =>
        response match {
          case TrySuccess(success) => {
            // Decide whether to close the connection or not.
            val keepAlive = isKeepAlive(httpRequest)

            // Write the response.
            val channelFuture = channel.write(nettyConversions.toNettyResponse(success))

            // Close the non-keep-alive connection after the write operation is done.
            if (!keepAlive) {
              channelFuture.addListener(ChannelFutureListener.CLOSE)
            }
          }
          case TryFailure(failure) => {
            handleException(failure)
          }
        }
      }
    } catch {
      case throwable: Throwable => {
        handleException(throwable)
      }
    }
  }
}

object HttpServer {
  def start(port: Int, requestHandler: Request => Future[Response])(implicit nettyConversions: NettyConversions, executionContext: ExecutionContext): IO[ServerBootstrap] = IO{
    val bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newFixedThreadPool(100),
        Executors.newFixedThreadPool(100)
      )
    )
    bootstrap.setPipelineFactory(new HttpServerInitializer(requestHandler))

    bootstrap.bind(new InetSocketAddress(port))

    bootstrap
  }

  def stop(serverBootstrap: ServerBootstrap): IO[Unit] = IO{
    serverBootstrap.releaseExternalResources()
    serverBootstrap.shutdown()
  }

  def runAndWait(port: Int, requestHandler: Request => Future[Response])(implicit nettyConversions: NettyConversions, executionContext: ExecutionContext): IO[Unit] = for{
    serverBootstrap <- start(port, requestHandler)
    _ <- putStrLn("Server Started On Port %s: Press Enter To Shutdown.".format(port))
    _ <- readLn
    _ <- stop(serverBootstrap)
    _ <- putStrLn("Server Stopped.")
  } yield ()
}