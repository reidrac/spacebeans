package net.usebox.gemini.server

import com.monovore.decline._

import cats.implicits._

import org.log4s._

object ServerApp {
  private val logger = getLogger

  val appName = "SpaceBeans Gemini Server"
  val version = "0.1.0"
  val defConfFile = "/etc/spacebeans.conf"

  case class ServerOpts(
      version: Boolean,
      confFile: String
  )

  val opts: Command[ServerOpts] =
    Command(
      name = "server",
      header = appName
    ) {
      (
        Opts
          .flag("version", "Display the version and exit.")
          .orFalse,
        Opts
          .option[String](
            "conf",
            s"Configuration file (default: $defConfFile).",
            short = "c"
          )
          .withDefault(defConfFile)
      ).mapN { (version, confFile) =>
        ServerOpts(version, confFile)
      }
    }

  def main(args: Array[String]): Unit =
    opts.parse(args.toIndexedSeq) match {
      case Left(help) =>
        println(help)
      case Right(ServerOpts(true, _)) =>
        println(version)
      case Right(ServerOpts(_, confFile)) =>
        ServiceConf.load(confFile) match {
          case Left(error) =>
            logger
              .error(
                s"Error reading $confFile: $error"
              )
          case Right(conf) =>
            logger.info(
              s"Starting $appName $version, listening on ${conf.address}:${conf.port}"
            )
            Server(conf).serve
        }
    }
}
