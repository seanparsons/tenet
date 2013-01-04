scalaVersion := "2.10.0"

crossScalaVersions := Seq("2.10.0")

crossVersion := CrossVersion.full

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.0.0-M7"

libraryDependencies += "org.scalaz" %% "scalaz-effect" % "7.0.0-M7"

libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % "7.0.0-M7"

libraryDependencies += "io.netty" % "netty" % "4.0.0.Beta1-SNAPSHOT"

libraryDependencies += ("io.argonaut" %% "argonaut" % "6.0-SNAPSHOT").cross(CrossVersion.full)

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.6.6"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.9" % "test"

initialCommands := """
import scalaz._
import Scalaz._
import scalaz.concurrent._
import scalaz.effect._
import com.github.seanparsons.tenet._
"""
