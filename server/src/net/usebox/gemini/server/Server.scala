package net.usebox.gemini.server

import java.nio.charset.Charset
import javax.net.ssl.SSLEngine
import java.net.URI
import java.nio.file.{Path, FileSystems, Files}

import scala.util.{Try, Success => TrySuccess}

import org.log4s._

import akka.stream._
import akka.stream.scaladsl._
import akka.actor.ActorSystem
import akka.util.ByteString

import URIUtils._

case class Server(conf: ServiceConf) {

  implicit val system = ActorSystem("space-beans")
  implicit val ec = system.dispatcher

  private[this] val logger = getLogger

  val defPort = 1965

  val mimeTypes = conf.mimeTypes
  val defaultMimeType = conf.defaultMimeType
  val vHosts = conf.virtualHosts

  val charsetDecoder = Charset.forName("utf-8").newDecoder()
  def decodeUTF8(value: ByteString): Either[Throwable, String] =
    Try(charsetDecoder.decode(value.toByteBuffer).toString()).toEither

  def validPath(path: String): Boolean =
    !path
      .split('/')
      .drop(1)
      .foldLeft(List(0)) {
        case (acc, "..") => acc.appended(acc.last - 1)
        case (acc, ".")  => acc.appended(acc.last)
        case (acc, _)    => acc.appended(acc.last + 1)
      }
      .exists(_ < 0)

  def guessMimeType(path: Path, params: Option[String]): String =
    mimeTypes.fold {
      List(".gmi", ".gemini")
        .find(path.toString().endsWith(_))
        .fold {
          Try(Files.probeContentType(path)).toOption match {
            case Some(mime) if mime != null => mime
            case _                          => defaultMimeType
          }
        }(_ => "text/gemini")
    } { types =>
      types
        .find {
          case (t, exts) => exts.exists(path.toString().endsWith(_))
        }
        .fold(defaultMimeType) { case (t, _) => t }
    } match {
      case mime @ "text/gemini" =>
        params.fold(mime)(p => s"$mime; ${p.stripMargin(';').trim()}")
      case mime => mime
    }

  def handleReq(req: String, remoteAddr: String): Response =
    (for {
      uri <- Try(URI.create(req)).toEither
      resp <- Try(
        (
          uri.getScheme(),
          uri.getHost(),
          uri.getPath().decode(),
          vHosts.find(_.host == uri.getHost())
        ) match {
          case (null, _, _, _) =>
            logger.debug(s"no scheme")
            BadRequest(req)
          case _ if uri.getPort() != -1 && uri.getPort() != conf.port =>
            logger.debug(s"invalid port, is a proxy request")
            ProxyRequestRefused(req)
          case _ if uri.getPort() == -1 && conf.port != defPort =>
            logger.debug(
              s"default port but non default was configured, is a proxy request"
            )
            ProxyRequestRefused(req)
          case ("gemini", host, _, None) =>
            logger.debug(s"vhost $host not found in $vHosts")
            ProxyRequestRefused(req)
          case ("gemini", host, _, _) if uri.getUserInfo() != null =>
            logger.debug(s"user info present")
            BadRequest(req, "Userinfo component is not allowed")
          case ("gemini", _, path, _) if !validPath(path) =>
            logger.debug("invalid path, out of root")
            BadRequest(req)
          case ("gemini", _, _, _) if uri.normalize() != uri =>
            logger.debug("redirect to normalize uri")
            PermanentRedirect(req, uri.normalize().toString())
          case ("gemini", host, rawPath, Some(vhost)) =>
            val (root, path) = vhost.getRoot(rawPath)

            val resource = FileSystems
              .getDefault()
              .getPath(root, path)
              .normalize()
            val cgi = vhost.getCgi(resource)

            logger.debug(s"requesting: '$resource', cgi is '$cgi'")

            resource.toFile() match {
              case file
                  if cgi
                    .map(_.toFile())
                    .map(f => f.isFile() && f.canExecute())
                    .getOrElse(false) =>
                logger.debug("is cgi, will execute")

                val queryString =
                  if (uri.getQuery() == null) "" else uri.getQuery()
                val pathInfo =
                  if (cgi.get.compareTo(resource) == 0) ""
                  else
                    "/" + resource
                      .subpath(
                        cgi.get.getNameCount(),
                        resource.getNameCount()
                      )
                      .toString()

                Cgi(
                  req,
                  filename = cgi.get.toString(),
                  queryString = queryString,
                  pathInfo = pathInfo,
                  scriptName = resource.getFileName().toString(),
                  host = vhost.host,
                  port = conf.port.toString(),
                  remoteAddr = remoteAddr
                )
              case path if !path.exists() =>
                logger.debug("no resource")
                NotFound(req)
              case path if path.exists() && !path.canRead() =>
                logger.debug("no read permissions")
                NotFound(req)
              case file if file.getName().startsWith(".") =>
                logger.debug("dot file, ignored request")
                NotFound(req)
              case file if file.isFile() =>
                Success(
                  req,
                  meta = guessMimeType(resource, vhost.geminiParams),
                  bodySize = file.length(),
                  bodyPath = Some(resource)
                )
              case dir
                  if dir.isDirectory() && !path.isEmpty() && !path
                    .endsWith("/") =>
                logger.debug("redirect directory")
                PermanentRedirect(req, uri.toString() + "/")
              case dir if dir.isDirectory() =>
                val dirFilePath = resource.resolve(vhost.indexFile)
                val dirFile = dirFilePath.toFile()

                if (dirFile.isFile() && dirFile.canRead()) {
                  logger.debug(s"serving index file: $dirFilePath")
                  Success(
                    req,
                    meta = guessMimeType(dirFilePath, vhost.geminiParams),
                    bodySize = dirFile.length(),
                    bodyPath = Some(dirFilePath)
                  )
                } else if (vhost.getDirectoryListing(resource)) {
                  logger.debug("directory listing")
                  DirListing(
                    req,
                    meta = "text/gemini",
                    bodyPath = Some(resource),
                    uriPath = path
                  )
                } else
                  NotFound(req)
              case _ =>
                logger.debug("default: other resource type")
                NotFound(req)
            }
          case (scheme, _, _, _) =>
            logger.debug(s"scheme $scheme not allowed")
            ProxyRequestRefused(req)
        }
      ).toEither
    } yield resp) match {
      case Left(error: IllegalArgumentException) =>
        logger.debug(s"invalid request: ${error.getMessage()}")
        BadRequest(req)
      case Left(error) =>
        logger.error(error)("Internal server error")
        PermanentFailure(req, "Internal server error")

      case Right(resp) => resp
    }

  def serve = {
    val certs = vHosts.map { vhost =>
      vhost.keyStore.fold(
        (
          vhost.host,
          TLSUtils.genSelfSignedCert(vhost.host, conf.genCertValidFor)
        )
      ) {
        case KeyStore(path, alias, password) =>
          TLSUtils
            .loadCert(path, alias, password)
            .fold(
              err => {
                logger
                  .error(err)(s"Failed to load $alias cert from keystore $path")
                system.terminate()
                throw err
              },
              r => (vhost.host, r)
            )
      }
    }.toMap

    val sslContext = TLSUtils.genSSLContext(certs)

    certs.foreach {
      case (host, (cert, _)) =>
        logger.info(s"Certificate for ${host} - serial-no: ${cert
          .getSerialNumber()}, final-date: ${cert.getNotAfter()}")
    }

    def createSSLEngine: SSLEngine = {
      val engine = sslContext.createSSLEngine()

      engine.setUseClientMode(false)
      engine.setEnabledCipherSuites(conf.enabledCipherSuites.toArray)
      engine.setEnabledProtocols(conf.enabledProtocols.toArray)

      engine
    }

    Tcp()
      .bindWithTls(
        conf.address,
        conf.port,
        () => createSSLEngine,
        backlog = 100,
        options = Nil,
        idleTimeout = conf.idleTimeout,
        verifySession = _ => TrySuccess(()),
        closing = TLSClosing.ignoreCancel
      )
      .runForeach { connection =>
        val remoteHost = connection.remoteAddress.getHostString()
        logger.debug(s"new connection $remoteHost")

        val handler = Flow[ByteString]
          .watchTermination() { (_, f) =>
            f.onComplete {
              _.toEither.swap.map(error =>
                logger.warn(
                  s"$remoteHost - stream terminated: ${error.getMessage()}"
                )
              )
            }
          }
          .via(
            Framing
              .delimiter(
                ByteString("\r\n"),
                maximumFrameLength = Server.maxReqLen + 1,
                allowTruncation = true
              )
          )
          .prefixAndTail(1)
          .map {
            case (Seq(req), tail) =>
              tail.run()
              decodeUTF8(req) match {
                case Left(error) =>
                  logger.debug(s"invalid UTF-8 encoding: ${error.getMessage()}")
                  BadRequest(req.utf8String)
                case Right(reqStr) =>
                  if (req.size > Server.maxReqLen)
                    BadRequest(reqStr.take(1024) + "{...}")
                  else
                    handleReq(reqStr, remoteHost)
              }
          }
          .take(1)
          .wireTap(resp =>
            logger.info(
              s"""$remoteHost "${resp.req}" ${resp.status} ${resp.bodySize}"""
            )
          )
          .flatMapConcat(_.toSource)

        connection.handleWith(handler)
      }
  }
}

object Server {

  /** Maximum request length in bytes. */
  val maxReqLen = 1024
}
