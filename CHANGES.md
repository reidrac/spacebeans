# What's new?

## Release 1.1.2 - 2021-03-02

 - Disable debug logging that was left on by mistake

## Release 1.1.1 - 2021-03-02

 - Fix: reviewed example configuration file to include TLS 1.3 ciphers

   This was preventing TLS 1.3 from being negotiated.

   If you have deployed SpaceBeans already, add these too entries to your cipher list:

```
    "TLS_AES_128_GCM_SHA256",
    "TLS_AES_256_GCM_SHA384"
```

   Remember to restart your service.

   See: https://wiki.mozilla.org/Security/Server_Side_TLS

 - Fix: avoid reverse lookups when logging

## Release 1.1.0 - 2021-02-28

 - User directories support:

```
// in virtual host
user-directories = true
user-directory-path = "/home/{user}/public_gemini/"
```

Won't check for the user on the system, it only translates requests based on the user specific root path.

For example:

```
gemini://host/~myuser/
```

Will use as root:

```
/home/myuser/public_gemini/
```

 - Support for per directory flags via `directories`:

```
// directory listing disabled for the virtual host
directory-listing = false

// but it is enabled for ROOT/directory/
directories = [
    { path = "directory/", directory-listing = true }
]
```

 - Removed some weight from the distribution bundle

## Release 1.0.1 - 2021-02-26

 - Support for JRE 8

## Release 1.0.0 - 2021-02-25

 - First public release

