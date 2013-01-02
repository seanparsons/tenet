scalaVersion := "2.9.2"

crossScalaVersions := Seq("2.9.2", "2.10.0-RC5")

crossVersion := CrossVersion.full

libraryDependencies += ("org.scalaz" %% "scalaz-core" % "7.0.0-M6").cross(CrossVersion.full)

libraryDependencies += ("org.scalaz" %% "scalaz-effect" % "7.0.0-M6").cross(CrossVersion.full)

libraryDependencies += ("org.scalaz" %% "scalaz-concurrent" % "7.0.0-M6").cross(CrossVersion.full)

libraryDependencies += "io.netty" % "netty" % "4.0.0.Alpha7"

initialCommands := """
import scalaz._
import Scalaz._
import scalaz.concurrent._
import scalaz.effect._
import com.github.seanparsons.tenet._
"""
