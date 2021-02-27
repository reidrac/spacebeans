package net.usebox.gemini.server

import org.log4s._

object ServerApp {
  private val logger = getLogger

  val appName = "SpaceBeans Gemini Server"
  val version = BuildInfo.version
  val defConfFile = "/etc/spacebeans.conf"

  case class ServerOpts(
      confFile: String
  )

  val parser = new scopt.OptionParser[ServerOpts](BuildInfo.name) {
    head(appName, BuildInfo.version)

    opt[String]('c', "conf")
      .action((x, c) => c.copy(confFile = x))
      .text(s"Configuration file (default: $defConfFile)")

    help("help").text("Displays this help and exits")
    version("version")
    note("\nProject page: https://github.com/reidrac/spacebeans")
  }

  def main(args: Array[String]): Unit =
    parser.parse(args, ServerOpts(defConfFile)) match {
      case Some(ServerOpts(confFile)) =>
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
      case None => // will display error
    }
}
