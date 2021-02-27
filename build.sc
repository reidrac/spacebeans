import mill._
import mill.scalalib._
import scalafmt._

import $ivy.`com.lihaoyi::mill-contrib-buildinfo:$MILL_VERSION`
import mill.contrib.buildinfo.BuildInfo

object server extends ScalaModule with ScalafmtModule with BuildInfo {
  def scalaVersion = "2.13.5"

  def scalacOptions = Seq(
    // features
  "-encoding", "utf-8",
  "-explaintypes",
  "-language:higherKinds",
  // warnings
  "-deprecation",
  "-Xlint:unused",
  "-unchecked",
  )

  def ivyDeps = Agg(
    ivy"com.github.pureconfig::pureconfig:0.14.0",
    ivy"com.github.scopt::scopt:4.0.0",
    ivy"org.log4s::log4s:1.8.2",
    ivy"ch.qos.logback:logback-classic:1.2.3",
    ivy"com.typesafe.akka::akka-stream:2.6.12",
    ivy"org.bouncycastle:bcprov-jdk15to18:1.68"
  )

  override def compile = T {
    reformat().apply()
    super.compile()
  }

  def gitHead = T.input { os.proc('git, "rev-parse", "HEAD").call().out.trim }

  def getVersion = T.input {
    val tag = try Option(
      os.proc('git, 'describe, "--exact-match", "--tags", "--always", gitHead()).call().out.trim
    )
    catch { case e => None }

    tag match {
      case Some(t) => t
      case None =>
        val latestTaggedVersion = os.proc('git, 'describe, "--abbrev=0", "--always", "--tags").call().out.trim
        val latestCommit = gitHead().take(6)
        s"$latestTaggedVersion-$latestCommit"
    }
  }

  val name = "spacebeans"
  def buildInfoMembers: T[Map[String, String]] = T {
    Map(
      "name" -> name,
      "version" -> getVersion().drop(1) // version tags start with v
    )
  }
  def buildInfoPackageName = Some("net.usebox.gemini.server")

  object test extends Tests with ScalafmtModule {
    def ivyDeps = Agg(ivy"org.scalatest::scalatest:3.2.2")
    def testFrameworks = Seq("org.scalatest.tools.Framework")

    override def compile = T {
      reformat().apply()
      super.compile()
    }

    def testOnly(args: String*) = T.command {
      super.runMain("org.scalatest.run", args: _*)
    }
  }
}
