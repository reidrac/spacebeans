package net.usebox.gemini.server

import java.io.FileInputStream
import java.time.Instant
import java.math.BigInteger
import java.util.Date
import java.security.cert.X509Certificate
import java.security.{
  Security,
  KeyStore,
  SecureRandom,
  PrivateKey,
  KeyPairGenerator,
  Principal
}
import java.security.KeyStore.PrivateKeyEntry
import java.net.Socket
import javax.net.ssl.{
  SSLContext,
  TrustManagerFactory,
  KeyManagerFactory,
  X509ExtendedKeyManager,
  SSLEngine,
  ExtendedSSLSession,
  StandardConstants,
  SNIHostName
}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.Try

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.x509.X509V3CertificateGenerator
import org.bouncycastle.jce.X509Principal

import org.log4s._

object TLSUtils {

  private[this] val logger = getLogger

  Security.addProvider(new BouncyCastleProvider())

  // https://github.com/grahamedgecombe/netty-sni-example/
  class SniKeyManager(keyManager: X509ExtendedKeyManager, defaultAlias: String)
      extends X509ExtendedKeyManager {
    override def getClientAliases(
        keyType: String,
        issuers: Array[Principal]
    ): Array[String] = throw new UnsupportedOperationException()

    override def chooseClientAlias(
        keyType: Array[String],
        issuers: Array[Principal],
        socker: Socket
    ): String = throw new UnsupportedOperationException()

    override def chooseEngineClientAlias(
        keyType: Array[String],
        issuers: Array[Principal],
        engine: SSLEngine
    ): String = throw new UnsupportedOperationException()

    override def getServerAliases(
        keyType: String,
        issuers: Array[Principal]
    ): Array[String] = keyManager.getServerAliases(keyType, issuers)

    override def chooseServerAlias(
        keyType: String,
        issuers: Array[Principal],
        socket: Socket
    ): String = keyManager.chooseServerAlias(keyType, issuers, socket)

    override def chooseEngineServerAlias(
        keyType: String,
        issuers: Array[Principal],
        engine: SSLEngine
    ): String =
      engine
        .getHandshakeSession()
        .asInstanceOf[ExtendedSSLSession]
        .getRequestedServerNames()
        .asScala
        .collectFirst {
          case n: SNIHostName
              if n.getType() == StandardConstants.SNI_HOST_NAME =>
            n.getAsciiName()
        } match {
        case Some(hostname)
            if hostname != null && getCertificateChain(
              hostname
            ) != null && getPrivateKey(hostname) != null =>
          hostname
        case _ => defaultAlias
      }

    override def getCertificateChain(alias: String): Array[X509Certificate] =
      keyManager.getCertificateChain(alias)

    override def getPrivateKey(alias: String): PrivateKey =
      keyManager.getPrivateKey(alias)
  }

  object SniKeyManager {
    def apply(
        keyManagerFactory: KeyManagerFactory,
        defaultAlias: String
    ): Either[Throwable, SniKeyManager] =
      keyManagerFactory.getKeyManagers.find(
        _.isInstanceOf[X509ExtendedKeyManager]
      ) match {
        case Some(km: X509ExtendedKeyManager) =>
          Right(new SniKeyManager(km, defaultAlias))
        case _ =>
          Left(
            new RuntimeException(
              "No X509ExtendedKeyManager: SNI support is not available"
            )
          )
      }
  }

  def loadCert(
      path: String,
      alias: String,
      password: String
  ): Either[Throwable, (X509Certificate, PrivateKey)] =
    Try {
      val fis = new FileInputStream(path)
      val ks = KeyStore.getInstance("JKS")
      ks.load(fis, password.toCharArray())
      fis.close()

      (
        ks.getCertificate(alias).asInstanceOf[X509Certificate],
        ks.getEntry(
            alias,
            new KeyStore.PasswordProtection(password.toCharArray())
          )
          .asInstanceOf[PrivateKeyEntry]
          .getPrivateKey()
      )
    }.toEither

  def genSelfSignedCert(
      host: String,
      validFor: FiniteDuration
  ): (X509Certificate, PrivateKey) = {

    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val kp = keyPairGenerator.generateKeyPair()

    val v3CertGen = new X509V3CertificateGenerator()
    v3CertGen.setSerialNumber(
      BigInteger.valueOf(new SecureRandom().nextInt()).abs()
    )
    v3CertGen.setIssuerDN(
      new X509Principal("CN=" + host + ", OU=None, O=None L=None, C=None")
    )
    v3CertGen.setNotBefore(
      Date.from(Instant.now().minusSeconds(60 * 60))
    )
    v3CertGen.setNotAfter(
      Date.from(Instant.now().plusSeconds(validFor.toSeconds))
    )
    v3CertGen.setSubjectDN(
      new X509Principal("CN=" + host + ", OU=None, O=None L=None, C=None")
    )

    v3CertGen.setPublicKey(kp.getPublic())
    v3CertGen.setSignatureAlgorithm("SHA256WithRSAEncryption")

    (v3CertGen.generateX509Certificate(kp.getPrivate()), kp.getPrivate())
  }

  def genSSLContext(
      certs: Map[String, (X509Certificate, PrivateKey)]
  ): SSLContext = {

    val ks = KeyStore.getInstance("JKS")
    ks.load(null, "secret".toCharArray())
    certs.foreach {
      case (hostname, (cert, pk)) =>
        ks.setKeyEntry(
          hostname,
          pk,
          "secret".toCharArray(),
          List(cert).toArray
        )
    }

    val tmFac = TrustManagerFactory.getInstance("SunX509")
    tmFac.init(ks)

    val kmFac = KeyManagerFactory.getInstance("SunX509")
    kmFac.init(ks, "secret".toCharArray())

    val sniKeyManager = SniKeyManager(kmFac, "localhost").left.map { err =>
      logger.warn(err)(s"Failed to init SNI")
    }

    val ctx = SSLContext.getInstance("TLSv1.2")
    ctx.init(
      sniKeyManager.fold(_ => kmFac.getKeyManagers, km => Array(km)),
      tmFac.getTrustManagers,
      new SecureRandom
    )
    ctx
  }

}
