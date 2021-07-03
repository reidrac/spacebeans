package net.usebox.gemini.server

import java.nio.file.FileSystems

import scala.concurrent.duration._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ServiceConfSpec extends AnyFlatSpec with Matchers {

  def getPath(value: String) =
    FileSystems.getDefault().getPath(getClass.getResource("/").getPath(), value)

  behavior of "getDirectoryListing"

  it should "resolve directory listing using vhost conf if no directory override" in {
    val vh = TestData.conf.virtualHosts.head
    vh.getDirectoryListing(getPath("/dir")) shouldBe vh.directoryListing
  }

  it should "resolve directory listing using directory override" in {
    val vh = TestData.conf.virtualHosts.head.copy(
      directoryListing = false,
      directories = List(
        Directory(
          getPath("dir").toString(),
          directoryListing = Some(true),
          None
        )
      )
    )
    vh.getDirectoryListing(getPath("dir")) shouldBe true
  }

  it should "ignore non matching directories resolving directory listing" in {
    val vh = TestData.conf.virtualHosts.head.copy(
      directoryListing = false,
      directories = List(
        Directory(
          getPath("no-match").toString(),
          directoryListing = Some(true),
          None
        )
      )
    )
    vh.getDirectoryListing(getPath("dir")) shouldBe false
  }

  behavior of "getCgi"

  it should "return None as allow CGI is off by default" in {
    val vh = TestData.conf.virtualHosts.head
    vh.getCgi(getPath("dir/cgi")) shouldBe None
  }

  it should "set allow CGI via directory override" in {
    List(true, false).foreach { value =>
      val vh = TestData.conf.virtualHosts.head.copy(
        directories = List(
          Directory(
            getPath("dir").toString(),
            directoryListing = None,
            Some(value)
          )
        )
      )
      vh.getCgi(getPath("dir/cgi")) should matchPattern {
        case Some(_) if value =>
        case None if !value   =>
      }
    }
  }

  it should "return the CGI path minus path info" in {
    val vh = TestData.conf.virtualHosts.head.copy(
      directories = List(
        Directory(
          getPath("dir").toString(),
          directoryListing = None,
          Some(true)
        )
      )
    )
    vh.getCgi(getPath("dir/cgi/path/info")) shouldBe Some(
      getPath("dir/cgi")
    )
  }

  it should "not return the CGI path if is exactly the CGI dir" in {
    val vh = TestData.conf.virtualHosts.head.copy(
      directories = List(
        Directory(
          getPath("dir").toString(),
          directoryListing = None,
          Some(true)
        )
      )
    )
    vh.getCgi(getPath("dir")) shouldBe None
    vh.getCgi(getPath("dir/")) shouldBe None
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
  }
}
