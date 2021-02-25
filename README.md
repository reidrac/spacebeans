# SpaceBeans Gemini Server

This is an experimental server for the [Gemini](https://gemini.circumlunar.space/) protocol.

It is built using [Scala](https://www.scala-lang.org/) and [Akka Streams](https://doc.akka.io/docs/akka/current/stream/index.html). The name tries to link the Gemini *theme* with the fact that the
server runs on the Java Virtual Machine.

Some of the **SpaceBeans** features:

 - Static files, including optional directory listings
 - IPv4 and IPv6
 - Configurable MIME types, or a built-in resolver
 - Virtual hosting, with SNI support
 - User provided certificates or auto-generated in memory (for development)
 - Configurable SSL engine (e.g. TLSv1.2 and/or TLSv1.3), with configurable ciphers

Check [CHANGES](CHANGES.md) to see what's new in the latest release.

## How to run it

Download [the `jar` distribution file](https://github.com/reidrac/spacebeans/releases/) and install Java Runtime Environment 8 (or
later; [openJRE](https://adoptopenjdk.net/) recommended).

You can run the service with:
```
java -jar spacebeans-VERSION.jar -c spacebeans.conf
```

You can also run the server with `--help` flag for CLI help.

Please check [the example configuration file](spacebeans.conf.example) for instructions on
how to configure the service.

### Running it as a service

TODO: instructions with systemd or similar.

## On security

You should evaluate your security requirements when running **SpaceBeans**.

In this section *TOFU* refers to "Trust On First Use".

### Auto-generated self-signed certificate

This is the easiest option, no need to store securely the certificate. The
downside is that you get a new certificate every time you start the service,
and that's bad for TOFU validation.

This is recommended **only for development** and not for a service facing the
Internet.

Comment out the `key-store` section on your virtual host and you are done.

### Self-signed certificate

You can generate a self signed certificate using Java's `keytool`:
```
keytool -genkey -keyalg RSA -alias ALIAS -keystore keystore.jks -storepass SECRET -validity 36500 -keysize 2048
```

When entering the certificate details, use the domain name as `CN`.

In the configuration file provide the path to the keystore, the alias and the
secret used when generating the certificate.

This is the recommended TOFU-compatible way of managing certificates. The
certificate **should be set to expire way in the future** because changing
certificates doesn't play well with TOFU validation.

### Import a CA signed certificate

The certificate has to be converted and imported into a JKS keystore to be
used by the server.

For example:
```
keytool -import -alias ALIAS -keystore keystore.jks -file cert.pem -storepass SECRET -noprompt
```

Answer "yes" when asked if the certificate should be trusted.

In the configuration file provide the path to the keystore, the alias and the
secret used when importing the certificate.

CA signed certificates don't play well with TOFU, but if the client is properly
validating the certificate, this is perfectly safe.

## Development

Requirements:

 - JDK 8 (or later; openjdk 8 or 11 recommended)
 - [Mill](https://com-lihaoyi.github.io/mill/) for building

Run the server with `mill server.run` and the tests with `mill server.test`.

## License

Copyright (C) 2021 Juan J. Martinez <jjm@usebox.net>  
This software is distributed under MIT license, unless stated otherwise.

See COPYING file.
