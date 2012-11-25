package com.github.seanparsons.tenet

import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpContentCompressor
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpHeaders._
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundMessageHandlerAdapter
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.Cookie
import io.netty.handler.codec.http.CookieDecoder
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpChunk
import io.netty.handler.codec.http.HttpChunkTrailer
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.{HttpRequest => NettyHttpRequest}
import io.netty.handler.codec.http.{HttpResponse => NettyHttpResponse}
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.ServerCookieEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.util.CharsetUtil
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.socket.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import scalaz._
import Scalaz._
import scalaz.concurrent._
import scalaz.effect._
import scalaz.effect.IO._
import scala.collection.JavaConverters._
import java.net.InetSocketAddress

case class Request(uri: String, method: String, headers: Map[String, List[String]])

object Request {
  val fromNettyRequest: (NettyHttpRequest) => Request = (nettyRequest: NettyHttpRequest) => {
    new Request(
      nettyRequest.getUri, 
      nettyRequest.getMethod.getName, 
      nettyRequest
        .getHeaders
        .asScala
        .toList
        .foldMap(entry => Map(entry.getKey -> List(entry.getValue)))
    )
  }
}

case class Response(statusCode: Int = 200, headers: Map[String, List[String]] = Map())

object Response {
  val defaultHeaders: Map[String, List[String]] = Map("Content-Length" -> List("0"))

  val toNettyResponse: (Response) => NettyHttpResponse = (response: Response) => {
    val nettyResponse = new DefaultHttpResponse(
      HTTP_1_1, 
      HttpResponseStatus.valueOf(response.statusCode)
    )
    (defaultHeaders ++ response.headers).foldLeft(nettyResponse){(workingResponse, header) =>
      workingResponse.setHeader(header._1, header._2.asJava)
      workingResponse
    }
  }
}

case class HttpServerInitializer(requestHandler: Request => Promise[Response]) extends ChannelInitializer[SocketChannel] {
  override def initChannel(channel: SocketChannel) {
    // Create a default pipeline implementation.
    val pipeline = channel.pipeline()

    pipeline.addLast("httpDecoder", new HttpRequestDecoder())
    // Uncomment the following line if you don't want to handle HttpChunks.
    //pipeline.addLast("aggregator", new HttpChunkAggregator(1048576))
    pipeline.addLast("httpEncoder", new HttpResponseEncoder())
    // Remove the following line if you don't want automatic content compression.
    pipeline.addLast("httpDeflater", new HttpContentCompressor())
    pipeline.addLast("handler", new HttpServerHandler(requestHandler))
  }
}

case class HttpServerHandler(requestHandler: Request => Promise[Response]) extends ChannelInboundMessageHandlerAdapter[NettyHttpRequest] {
  override def messageReceived(context: ChannelHandlerContext, httpRequest: NettyHttpRequest) {
    Request.fromNettyRequest(httpRequest) |> requestHandler map Response.toNettyResponse to {response =>
      // Decide whether to close the connection or not.
      val keepAlive = isKeepAlive(httpRequest)
      
      // Write the response.
      val channelFuture = context.write(response)

      // Close the non-keep-alive connection after the write operation is done.
      if (!keepAlive) {
        channelFuture.addListener(ChannelFutureListener.CLOSE)
      }
    }
  }
}

object HttpServer {
  def start(port: Int, requestHandler: Request => Promise[Response]): IO[ServerBootstrap] = IO{
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

  def runAndWait(port: Int, requestHandler: Request => Promise[Response]): IO[Unit] = for{
    serverBootstrap <- start(port, requestHandler)
    _ <- putStrLn("Server Started: Press Enter To Shutdown.")
    _ <- readLn
    _ <- stop(serverBootstrap)
    _ <- putStrLn("Server Stopped.")
  } yield ()
}
