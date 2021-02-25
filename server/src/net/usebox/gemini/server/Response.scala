package net.usebox.gemini.server

import java.nio.file.Path

import akka.stream.ActorAttributes
import akka.stream.scaladsl.{Source, FileIO}
import akka.util.ByteString

import URIUtils._

sealed trait Response {
  def req: String
  def status: Int
  def meta: String
  def bodyPath: Option[Path]
  def bodySize: Long

  def toSource =
    Source.single(ByteString(s"${status} ${meta}\r\n")) ++ (bodyPath match {
      case None => Source.empty
      case Some(path) =>
        FileIO
          .fromPath(path)
          .withAttributes(
            ActorAttributes.dispatcher("sb-blocking-dispatcher")
          )
    })
}

sealed trait NoContentResponse extends Response {
  var bodyPath: Option[Path] = None
  var bodySize: Long = 0
}

case class Success(
    req: String,
    meta: String = "Success",
    bodyPath: Option[Path] = None,
    bodySize: Long = 0
) extends Response {
  val status: Int = 20
}

case class DirListing(
    req: String,
    meta: String = "Success",
    uriPath: String,
    bodyPath: Option[Path] = None
) extends Response {
  val status: Int = 20

  val body: String = bodyPath.fold("") { path =>
    (List(s"# Index of ${uriPath}\n") ++
      (if (uriPath != "/") List(s"=> ../ ..") else Nil)
      ++
        path
          .toFile()
          .listFiles()
          .toList
          .sortBy {
            case f if f.isDirectory() => 0
            case f if f.isFile()      => 1
            case _                    => 2
          }
          .flatMap {
            case f if !f.canRead() || f.getName().startsWith(".") => None
            case f if f.isDirectory() =>
              Some(s"=> ${f.getName().encode()}/ ${f.getName()}/")
            case f => Some(s"=> ${f.getName().encode()} ${f.getName()}")
          }).mkString("\n") + "\n"
  }

  def bodySize: Long = body.size

  override def toSource =
    Source.single(ByteString(s"${status} ${meta}\r\n")) ++ Source.single(
      ByteString(body)
    )
}

case class TempRedirect(
    req: String,
    meta: String = "Redirect - temporary"
) extends NoContentResponse {
  val status: Int = 30
}

case class PermanentRedirect(
    req: String,
    meta: String = "Redirect - permanent"
) extends NoContentResponse {
  val status: Int = 31
}

case class TempFailure(
    req: String,
    meta: String = "Temporary failure"
) extends NoContentResponse {
  val status: Int = 40
}

case class NotAvailable(
    req: String,
    meta: String = "Server not available"
) extends NoContentResponse {
  val status: Int = 41
}

case class PermanentFailure(
    req: String,
    meta: String = "Permanent failure"
) extends NoContentResponse {
  val status: Int = 50
}

case class NotFound(
    req: String,
    meta: String = "Not found"
) extends NoContentResponse {
  val status: Int = 51
}

case class ProxyRequestRefused(
    req: String,
    meta: String = "Proxy request refused"
) extends NoContentResponse {
  val status: Int = 53
}

case class BadRequest(
    req: String,
    meta: String = "Bad request"
) extends NoContentResponse {
  val status: Int = 59
}
