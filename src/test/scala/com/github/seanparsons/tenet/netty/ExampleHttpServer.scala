package com.github.seanparsons.tenet.netty

import com.github.seanparsons.tenet.{Response, Request}
import scala.concurrent.Future


object ExampleHttpServer extends App {
  import scala.concurrent.ExecutionContext.Implicits._
  implicit val utf8Conversions = NettyConversions.createForCharset()

  val handler = (request: Request) => Future(Response().copy(body = request.body))

  HttpServer.runAndWait(8080, handler).unsafePerformIO()
}
