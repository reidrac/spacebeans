package net.usebox.gemini.server

import java.nio.file.FileSystems

import scala.concurrent.duration._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import akka.util.ByteString

class ServerSpec extends AnyFlatSpec with Matchers {

  def getPath(value: String) = FileSystems.getDefault().getPath(value)

  def getPath(root: String, dir: String) =
    FileSystems
      .getDefault()
      .getPath(root, dir)
      .normalize()

  behavior of "validPath"

  it should "return true for the emtpy path" in {
    Server(TestData.conf).validPath("") shouldBe true
  }

  it should "return true for valid paths" in {
    List("/", "/file", "/./", "/.", "/dir/", "/dir/../").foreach { p =>
      Server(TestData.conf).validPath(p) shouldBe true
    }
  }

  it should "return false for invalid paths" in {
    List("/../", "/..", "/dir/../..", "/dir/../..", "/./../", "/./dir/.././../")
      .foreach { p =>
        Server(TestData.conf).validPath(p) shouldBe false
      }
  }

  behavior of "guessMimeType using the internal resolver"

  it should "resolve a known MIME type" in {
    Server(TestData.conf)
      .guessMimeType(
        getPath("file.html"),
        None
      ) shouldBe "text/html"
  }

  it should "resolve de default MIME type for unknown types" in {
    Server(TestData.conf)
      .guessMimeType(
        getPath("unknow"),
        None
      ) shouldBe TestData.conf.defaultMimeType
  }

  it should "resolve gemini MIME type" in {
    Server(TestData.conf)
      .guessMimeType(
        getPath("file.gmi"),
        None
      ) shouldBe "text/gemini"
    Server(TestData.conf)
      .guessMimeType(
        getPath("file.gemini"),
        None
      ) shouldBe "text/gemini"
  }

  it should "resolve gemini MIME type, including parameters" in {
    Server(TestData.conf)
      .guessMimeType(
        getPath("file.gmi"),
        Some("param")
      ) shouldBe "text/gemini; param"
    Server(TestData.conf)
      .guessMimeType(
        getPath("file.gemini"),
        Some("param")
      ) shouldBe "text/gemini; param"
  }

  it should "gemini MIME type parameters are sanitized" in {
    Server(TestData.conf)
      .guessMimeType(
        getPath("file.gmi"),
        Some("     ; param")
      ) shouldBe "text/gemini; param"
  }

  behavior of "guessMimeType using the configured types"

  it should "resolve a known MIME type" in {
    Server(TestData.conf.copy(mimeTypes = TestData.mimeTypes))
      .guessMimeType(
        getPath("file.gmi"),
        None
      ) shouldBe "config"
  }

  it should "include parameters for text/gemini MIME types" in {
    Server(
      TestData.conf.copy(mimeTypes = Some(Map("text/gemini" -> List(".gmi"))))
    ).guessMimeType(
      getPath("file.gmi"),
      Some("param")
    ) shouldBe "text/gemini; param"
  }

  it should "resolve de default MIME type for unknown types" in {
    Server(TestData.conf.copy(mimeTypes = TestData.mimeTypes))
      .guessMimeType(
        getPath("unknow"),
        None
      ) shouldBe TestData.conf.defaultMimeType
  }

  behavior of "decodeUTF8"

  it should "return right on valid UTF-8 codes" in {
    Server(TestData.conf)
      .decodeUTF8(ByteString("válid UTF-8")) shouldBe Symbol(
      "right"
    )
  }

  it should "return left on invalid UTF-8 codes" in {
    Server(TestData.conf)
      .decodeUTF8(ByteString(Array(0xc3.toByte, 0x28.toByte))) shouldBe Symbol(
      "left"
    )
  }

  behavior of "handleReq"

  it should "return bad request on URLs with no scheme" in {
    Server(TestData.conf).handleReq("//localhost/") should matchPattern {
      case _: BadRequest =>
    }
  }

  it should "return proxy request refused on port mismatch" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost:8080/") should matchPattern {
      case _: ProxyRequestRefused =>
    }
  }

  it should "return proxy request refused when port not provided and configured port is not default" in {
    Server(TestData.conf.copy(port = 8080))
      .handleReq("gemini://localhost/") should matchPattern {
      case _: ProxyRequestRefused =>
    }
  }

  it should "return success when port is provided and matches configured port (not default)" in {
    Server(TestData.conf.copy(port = 8080))
      .handleReq("gemini://localhost:8080/") should matchPattern {
      case _: Success =>
    }
  }

  it should "return proxy request refused when the vhost is not found" in {
    Server(TestData.conf)
      .handleReq("gemini://otherhost/") should matchPattern {
      case _: ProxyRequestRefused =>
    }
  }

  it should "return bad request when user info is present" in {
    Server(TestData.conf)
      .handleReq("gemini://user@localhost/") should matchPattern {
      case _: BadRequest =>
    }
  }

  it should "return bad request when the path is out of root dir" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/../../") should matchPattern {
      case _: BadRequest =>
    }
  }

  it should "return bad request for invalid URLs" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/ invalid") should matchPattern {
      case _: BadRequest =>
    }
  }

  it should "redirect to normalize the URL" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/./") should matchPattern {
      case _: PermanentRedirect =>
    }
  }

  it should "return not found if the path doesn't exist" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/doesnotexist") should matchPattern {
      case _: NotFound =>
    }
  }

  it should "return not found if a dot file" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/.dotfile") should matchPattern {
      case _: NotFound =>
    }
  }

  it should "return success on reading file" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/index.gmi") should matchPattern {
      case Success(_, "text/gemini", Some(_), 25L) =>
    }
  }

  it should "redirect and normalize request on a directory" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/dir") should matchPattern {
      case _: PermanentRedirect =>
    }
  }

  it should "return an existing index file when requesting a directory" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/") should matchPattern {
      case Success(_, "text/gemini", Some(_), 25L) =>
    }
  }

  it should "return a directory listing if is enabled and no index" in {
    Server(TestData.conf)
      .handleReq("gemini://localhost/dir/") should matchPattern {
      case _: DirListing =>
    }
  }

  it should "return a directory listing, directory listing flags: vhost flag false, directories flag true" in {
    Server(
      TestData.conf.copy(virtualHosts =
        List(
          TestData.conf
            .virtualHosts(0)
            .copy(
              directoryListing = false,
              directories = List(
                Directory(
                  getPath(getClass.getResource("/").getPath(), "dir/")
                    .toString(),
                  directoryListing = Some(true)
                )
              )
            )
        )
      )
    ).handleReq("gemini://localhost/dir/") should matchPattern {
      case _: DirListing =>
    }
  }

  it should "return not found with no index, directory listing flags: vhost flag true, directories flag false" in {
    Server(
      TestData.conf.copy(virtualHosts =
        List(
          TestData.conf
            .virtualHosts(0)
            .copy(
              directoryListing = true,
              directories = List(
                Directory(
                  getPath(getClass.getResource("/").getPath(), "dir/")
                    .toString(),
                  directoryListing = Some(false)
                )
              )
            )
        )
      )
    ).handleReq("gemini://localhost/dir/") should matchPattern {
      case _: NotFound =>
    }
  }

  it should "return not found if directory listing is not enabled and no index" in {
    Server(
      TestData.conf.copy(virtualHosts =
        List(TestData.conf.virtualHosts(0).copy(directoryListing = false))
      )
    ).handleReq("gemini://localhost/dir/") should matchPattern {
      case _: NotFound =>
    }
  }

  it should "return proxy request refused for non gemini schemes" in {
    Server(TestData.conf)
      .handleReq("https://localhost/") should matchPattern {
      case _: ProxyRequestRefused =>
    }
  }

  it should "include gemini params for gemini MIME type" in {
    Server(
      TestData.conf.copy(virtualHosts =
        List(TestData.conf.virtualHosts(0).copy(geminiParams = Some("test")))
      )
    ).handleReq("gemini://localhost/index.gmi") should matchPattern {
      case Success(_, "text/gemini; test", Some(_), 25L) =>
    }
  }

  object TestData {
    val conf = ServiceConf(
      address = "127.0.0.1",
      port = 1965,
      defaultMimeType = "text/plain",
      idleTimeout = 10.seconds,
      virtualHosts = List(
        VirtualHost(
          host = "localhost",
          root = getClass.getResource("/").getPath()
        )
      ),
      genCertValidFor = 1.day,
      enabledProtocols = Nil,
      enabledCipherSuites = Nil
    )

    val mimeTypes = Some(
      Map(
        "config" -> List(".gmi", ".gemini")
      )
    )
  }
}
