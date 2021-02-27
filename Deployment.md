# Deploying SpaceBeans on Debian

This is simple "how to" to deploy the service on a stock Debian installation.

All commands need to be run as `root` user.

(tip: `sudo -i` if you're using sudo)

0. Install OpenJDK JRE headless:
```
apt install openjdk-8-jre-headless
```
(If Java 8 is not available, you can install 11 instead)

1. Create a system user:
```
groupadd spacebeans

adduser --quiet \
        --system \
        --shell /usr/sbin/nologin \
        --home /nonexistent \
        --ingroup spacebeans \
        --no-create-home \
        --disabled-password \
        spacebeans
```

2. Copy the server's binary to `/opt/spacebeans/`:
```
mkdir -p /opt/spacebeans
cd /opt/spacebeans
wget https://github.com/reidrac/spacebeans/releases/download/vVERSION/spacebeans-VERSION.jar
```

3. Create a certificate (optional, only if you don't have one already):
```
cd /opt/spacebeans
keytool -genkey -keyalg RSA -alias ALIAS -keystore keystore.jks -storepass SECRET -noprompt -validity 36500 -keysize 2048
chown spacebeans:spacebeans keystore.jks
chmod 0400 keystore.jks
```

When entering the certificate details, use the domain name as `CN`.

In the configuration file provide the path to the keystore, the alias and the
secret used when generating the certificate.

4. Prepare your `spacebeaans.conf` file.

Put it in `/opt/spacebeans/`, with at least one virtual host.

For example:
```
virtual-hosts = [
    {
        host = "*your domain*"
        root = "/var/gemini/*your domain*"
        index-file = "index.gmi"

        directory-listing = true

        key-store {
            path = "/opt/spacebeans/keystore.jks"
            alias = "*your domain*"
            password = "*your secret*"
        }
    }
]
```

Ensure that the file has the right permissions:
```
cd /opt/spacebeans
chown spacebeans:spacebeans spacebeans.conf
chmod 0400 spacebeans.conf
```

5. Create `/etc/systemd/system/spacebeans.service`:

```
[Unit]
Description=SpaceBeans Gemini Server
After=network.target

[Service]
Type=simple
Restart=always
RestartSec=5
User=spacebeans
ExecStart=/usr/bin/java -jar /opt/spacebeans/spacebeans-VERSION.jar -c /opt/spacebeans/spacebeans.conf

[Install]
WantedBy=multi-user.target
```

Then start the service:
```
systemctl start spacebeans.service
```

Check that it is up and running:
```
systemctl status spacebeans.service
```

(should say "Active: active (running)")

Then enable it so it starts after a reboot:
```
systemctl enable spacebeans.service
```

And you're done!

