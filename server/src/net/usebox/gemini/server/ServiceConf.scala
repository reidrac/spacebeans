package net.usebox.gemini.server

import java.nio.file.{Path, FileSystems}

import scala.concurrent.duration.FiniteDuration

import pureconfig._
import pureconfig.generic.semiauto._

import org.log4s._

case class KeyStore(path: String, alias: String, password: String)

case class Directory(path: String, directoryListing: Option[Boolean])

case class VirtualHost(
    host: String,
    root: String,
    keyStore: Option[KeyStore] = None,
    indexFile: String = "index.gmi",
    directoryListing: Boolean = true,
    geminiParams: Option[String] = None,
    directories: List[Directory]
)

object VirtualHost {
  implicit class VirtualHostOps(vhost: VirtualHost) {
    def getDirectoryListing(path: Path): Boolean =
      vhost.directories
        .find(_.path == path.toString())
        .fold(vhost.directoryListing)(loc =>
          loc.directoryListing.getOrElse(vhost.directoryListing)
        )
  }
}

case class ServiceConf(
    address: String,
    port: Int,
    idleTimeout: FiniteDuration,
    defaultMimeType: String,
    mimeTypes: Option[Map[String, List[String]]] = None,
    virtualHosts: List[VirtualHost],
    genCertValidFor: FiniteDuration,
    enabledProtocols: List[String],
    enabledCipherSuites: List[String]
)

object ServiceConf {

  private[this] val logger = getLogger

  implicit val keyStoreReader = deriveReader[KeyStore]
  implicit val directoryHostReader = deriveReader[Directory]
  implicit val virtualHostReader = deriveReader[VirtualHost]
  implicit val serviceConfReader = deriveReader[ServiceConf]

  def load(confFile: String) =
    ConfigSource.file(confFile).load[ServiceConf].map { conf =>
      conf.copy(virtualHosts = conf.virtualHosts.map { vhost =>
        vhost.copy(directories = vhost.directories.map { dir =>
          val path =
            FileSystems
              .getDefault()
              .getPath(vhost.root, dir.path)
              .normalize()

          if (!path.toFile().isDirectory())
            logger.warn(
              s"In virtual host '${vhost.host}': directory entry '${dir.path}' is not a directory"
            )

          dir
            .copy(path = path.toString())
        })
      })
    }
}
