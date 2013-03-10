package com.github.seanparsons.tenet.core

import scalaz.stream._
import scalaz._,Scalaz._
import scalaz.concurrent._
import scalaz.contrib.std._
import java.nio.channels._
import java.net._
import scala.collection.JavaConverters._

// Test.
object IOHost {
  def openServerSocket(port: Int) = Task{
      val selector = Selector.open()
      val server = ServerSocketChannel.open()
      server.socket().bind(new InetSocketAddress(port))
      server.configureBlocking(false)
      server.register(selector, SelectionKey.OP_ACCEPT)
      (server, selector)
    }
  val closeServerSocket: ((ServerSocketChannel, Selector)) => Task[Unit] = {case (server: ServerSocketChannel, selector: Selector) => Task{
    selector.close()
    server.socket().close()
    server.close()
  }}
  val produceSocketChannels: ((ServerSocketChannel, Selector)) => Task[Seq[SocketChannel]] = {case (server: ServerSocketChannel, selector: Selector) => Task{
    val selectedKeys = selector.selectedKeys().asScala
    selectedKeys.foreach{selectedKey =>
      if (selectedKey.isConnectable) selectedKey.channel().asInstanceOf[SocketChannel].finishConnect()
      if (selectedKey.isAcceptable()) { 
        val client = server.accept()
        client.configureBlocking(false)
        client.socket().setTcpNoDelay(true)
        client.register(selector, SelectionKey.OP_READ)
      }
    }
    val socketChannels = selectedKeys.toSeq.collect(key => key match {
      case readable if readable.isReadable() => key.channel.asInstanceOf[SocketChannel]
    })
    selector.selectedKeys.clear()
    socketChannels
  }}
  def selectorResource(port: Int) = io.resource[(ServerSocketChannel, Selector), Seq[SocketChannel]](openServerSocket(port))(closeServerSocket)(produceSocketChannels)
}
