package com.github.seanparsons.tenet.netty

import com.github.seanparsons.tenet.{Response, Request}
import scala.concurrent.Future
import argonaut._
import Argonaut._

object ExampleHttpServer extends App {
  import scala.concurrent.ExecutionContext.Implicits._
  implicit val utf8Conversions = NettyConversions.createForCharset()

  val handler = (request: Request) => Future(Response().copy(body = request.body))

  HttpServer.runAndWait(8080, handler).unsafePerformIO()
}

object JsonParsingExampleHttpServer extends App {
  import scala.concurrent.ExecutionContext.Implicits._
  implicit val utf8Conversions = NettyConversions.createForCharset()

  val handler = (request: Request) => {
    //println(request.body)
    Future(Response().copy(body = request.body.parseOption.fold(jSingleObject("error", jString("Error!")))(identity).nospaces))
  }

  HttpServer.runAndWait(8080, handler).unsafePerformIO()
}