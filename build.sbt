scalaVersion := "2.10.0"

crossScalaVersions := Seq("2.10.0")

crossVersion := CrossVersion.full

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.0.0-M7"

libraryDependencies += "org.scalaz" %% "scalaz-effect" % "7.0.0-M7"

libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % "7.0.0-M7"

libraryDependencies += "io.netty" % "netty" % "3.6.2.Final"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.2"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.9" % "test"

libraryDependencies += ("io.argonaut" %% "argonaut" % "6.0-SNAPSHOT").cross(CrossVersion.full)

initialCommands := """
import scalaz._
import Scalaz._
import scalaz.concurrent._
import scalaz.effect._
import com.github.seanparsons.tenet._
"""
