package net.usebox.gemini.server

import java.nio.charset.StandardCharsets
import java.net.{URLEncoder, URLDecoder}

import scala.util.Try

object URIUtils {
  // FIXME: decoding/encoding errors
  implicit class StringOps(s: String) {
    def encode(): String =
      Try(URLEncoder.encode(s, StandardCharsets.UTF_8.toString())).toOption
        .getOrElse(s)

    def decode(): String =
      Try(URLDecoder.decode(s, StandardCharsets.UTF_8.name())).toOption
        .getOrElse(s)
  }
}
