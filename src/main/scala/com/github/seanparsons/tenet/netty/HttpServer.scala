package com.github.seanparsons.tenet.netty

import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.{HttpRequest => NettyHttpRequest, HttpResponse => NettyHttpResponse, _}
import io.netty.handler.codec.http.HttpHeaders._
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundMessageHandlerAdapter
import io.netty.handler.codec.DecoderResult
import io.netty.util.CharsetUtil
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.socket.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.buffer.{ByteBufUtil, Unpooled, ByteBuf}
import io.netty.handler.logging.{LogLevel, MessageLoggingHandler}
import scalaz._
import Scalaz._
import scalaz.concurrent._
import scalaz.effect._
import scalaz.effect.IO._
import scala.collection.JavaConverters._
import java.net.InetSocketAddress
import com.github.seanparsons.tenet._
import java.nio.charset.Charset
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure => TryFailure, Success => TrySuccess}
import org.slf4j.LoggerFactory


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
      (defaultHeaders ++ response.headers).foldLeft(nettyResponse){(workingResponse, header) =>
        workingResponse.setHeader(header._1, header._2.asJava)
        workingResponse
      }
      nettyResponse.setContent(Unpooled.copiedBuffer(response.body, charset))
      nettyResponse
    }
  }
}

case class HttpServerInitializer(requestHandler: Request => Future[Response])(implicit nettyConversions: NettyConversions, executionContext: ExecutionContext) extends ChannelInitializer[SocketChannel] {
  override def initChannel(channel: SocketChannel) {
    // Create a default pipeline implementation.
    val pipeline = channel.pipeline()

    pipeline.addLast("httpDecoder", new HttpRequestDecoder())
    // Uncomment the following line if you don't want to handle HttpChunks.
    pipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    //pipeline.addLast("messageLogger", new MessageLoggingHandler("After Aggregator", LogLevel.INFO))
    pipeline.addLast("httpEncoder", new HttpResponseEncoder())
    // Remove the following line if you don't want automatic content compression.
    pipeline.addLast("httpDeflater", new HttpContentCompressor())
    pipeline.addLast("handler", new HttpServerHandler(requestHandler))
  }
}

case class HttpServerHandler(requestHandler: Request => Future[Response])(implicit nettyConversions: NettyConversions, executionContext: ExecutionContext) extends ChannelInboundMessageHandlerAdapter[NettyHttpRequest] {
  val logger = LoggerFactory.getLogger(getClass)

  override def messageReceived(context: ChannelHandlerContext, httpRequest: NettyHttpRequest) {
    def handleException(throwable: Throwable) {
      // Log the failure.
      logger.error("Error while processing request.", throwable)

      // Write the response.
      val channelFuture = context.write(nettyConversions.toNettyResponse(Response(statusCode = 500)))

      // Close the connection by default?
      channelFuture.addListener(ChannelFutureListener.CLOSE)
    }

    // Transform the response.
    try {
      logger.info("Starting the handler response.")
      val responseFuture = httpRequest |> nettyConversions.fromNettyRequest |> requestHandler
      responseFuture.onComplete{response =>
        response match {
          case TrySuccess(success) => {
            logger.info("Starting the success branch.")
            // Decide whether to close the connection or not.
            val keepAlive = isKeepAlive(httpRequest)

            // Write the response.
            val channelFuture = context.write(nettyConversions.toNettyResponse(success))

            // Close the non-keep-alive connection after the write operation is done.
            if (!keepAlive) {
              channelFuture.addListener(ChannelFutureListener.CLOSE)
            }
          }
          case TryFailure(failure) => {
            logger.info("Starting the failure branch.")
            handleException(failure)
          }
        }
      }
    } catch {
      case throwable: Throwable => {
        logger.info("Starting the failure fallback branch.")
        handleException(throwable)
      }
    }
  }
}

object HttpServer {
  def start(port: Int, requestHandler: Request => Future[Response])(implicit nettyConversions: NettyConversions, executionContext: ExecutionContext): IO[ServerBootstrap] = IO{
    val bootstrap = new ServerBootstrap()
    bootstrap
      .group(new NioEventLoopGroup(), new NioEventLoopGroup())
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new HttpServerInitializer(requestHandler))
      .localAddress(new InetSocketAddress(port))

    bootstrap.bind().sync()

    bootstrap
  }

  def stop(serverBootstrap: ServerBootstrap): IO[Unit] = IO{
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