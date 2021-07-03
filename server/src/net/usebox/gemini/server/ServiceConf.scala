package net.usebox.gemini.server

import java.nio.file.{Path, FileSystems}

import scala.concurrent.duration.FiniteDuration

import pureconfig._
import pureconfig.generic.semiauto._

import org.log4s._

case class KeyStore(path: String, alias: String, password: String)

case class Directory(
    path: String,
    directoryListing: Option[Boolean],
    allowCgi: Option[Boolean]
)

case class VirtualHost(
    host: String,
    root: String,
    keyStore: Option[KeyStore] = None,
    indexFile: String = "index.gmi",
    directoryListing: Boolean = true,
    geminiParams: Option[String] = None,
    directories: List[Directory] = Nil,
    userDirectories: Boolean = false,
    userDirectoryPath: Option[String] = None
)

object VirtualHost {

  val userTag = "{user}"
  val userRe = raw"/~([a-z_][a-z0-9_-]*)(/{1}.*)?".r

  implicit class VirtualHostOps(vhost: VirtualHost) {
    def getDirectoryListing(path: Path): Boolean =
      vhost.directories
        .find(_.path == path.toString())
        .flatMap(_.directoryListing)
        .getOrElse(vhost.directoryListing)

    def getCgi(path: Path): Option[Path] =
      vhost.directories
        .find(d =>
          path.startsWith(
            d.path
          ) && path.toString != d.path && d.allowCgi == Some(true)
        )
        .collect {
          case d =>
            val dp =
              FileSystems.getDefault().getPath(d.path).normalize()
            FileSystems
              .getDefault()
              .getPath(d.path, path.getName(dp.getNameCount()).toString())
        }

    def getRoot(path: String): (String, String) =
      path match {
        case userRe(user, null)
            if vhost.userDirectories && vhost.userDirectoryPath.nonEmpty =>
          // username with no end slash, force redirect
          (vhost.userDirectoryPath.get.replace(userTag, user), ".")
        case userRe(user, userPath)
            if vhost.userDirectories && vhost.userDirectoryPath.nonEmpty =>
          (vhost.userDirectoryPath.get.replace(userTag, user), userPath)
        case _ => (vhost.root, path)
      }
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

  import VirtualHost.userTag

  def load(confFile: String) =
    ConfigSource.file(confFile).load[ServiceConf].map { conf =>
      conf.copy(virtualHosts = conf.virtualHosts.map { vhost =>
        if (
          vhost.userDirectories && !vhost.userDirectoryPath
            .fold(false)(dir => dir.contains(userTag))
        )
          logger.warn(
            s"In virtual host '${vhost.host}': user-directories is enabled but $userTag not found in user-directory-path"
          )

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
