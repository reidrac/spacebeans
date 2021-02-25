package net.usebox.gemini.server

import pureconfig._
import pureconfig.generic.semiauto._

import scala.concurrent.duration.FiniteDuration

case class KeyStore(path: String, alias: String, password: String)

case class VirtualHost(
    host: String,
    root: String,
    keyStore: Option[KeyStore] = None,
    indexFile: String = "index.gmi",
    directoryListing: Boolean = true,
    geminiParams: Option[String] = None
)

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

  implicit val keyStoreReader = deriveReader[KeyStore]
  implicit val virtualHostReader = deriveReader[VirtualHost]
  implicit val serviceConfReader = deriveReader[ServiceConf]

  def load(confFile: String) = ConfigSource.file(confFile).load[ServiceConf]
}
