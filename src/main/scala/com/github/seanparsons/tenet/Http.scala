package com.github.seanparsons.tenet


import scalaz._
import Scalaz._

case class Request(uri: String, method: String = "GET", headers: Map[String, List[String]] = Map(), body: String = "")

object Request {

}

case class Response(statusCode: Int = 200, headers: Map[String, List[String]] = Response.defaultHeaders, body: String = "")

object Response {
  val defaultHeaders: Map[String, List[String]] = Map()
}