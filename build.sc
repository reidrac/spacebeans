import mill._
import mill.scalalib._
import scalafmt._

object server extends ScalaModule with ScalafmtModule {
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
    ivy"com.monovore::decline:1.3.0",
    ivy"org.log4s::log4s:1.8.2",
    ivy"ch.qos.logback:logback-classic:1.2.3",
    ivy"com.typesafe.akka::akka-stream:2.6.12",
    ivy"org.bouncycastle:bcprov-jdk15to18:1.68"
  )

  override def compile = T {
    reformat().apply()
    super.compile()
  }

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
