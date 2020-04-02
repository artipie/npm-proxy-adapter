<img src="https://www.artipie.com/logo.svg" width="64px" height="64px"/>

[![EO principles respected here](https://www.elegantobjects.org/badge.svg)](https://www.elegantobjects.org)
[![DevOps By Rultor.com](http://www.rultor.com/b/artipie/npm-proxy-adapter)](http://www.rultor.com/p/artipie/npm-proxy-adapter)
[![We recommend IntelliJ IDEA](https://www.elegantobjects.org/intellij-idea.svg)](https://www.jetbrains.com/idea/)

[![Build Status](https://img.shields.io/travis/artipie/npm-proxy-adapter/master.svg)](https://travis-ci.org/artipie/npm-proxy-adapter)
[![Javadoc](http://www.javadoc.io/badge/com.artipie/npm-proxy-adapter.svg)](http://www.javadoc.io/doc/com.artipie/npm-proxy-adapter)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/artipie/npm-proxy-adapter/blob/master/LICENSE.txt)
[![Hits-of-Code](https://hitsofcode.com/github/artipie/npm-proxy-adapter)](https://hitsofcode.com/view/github/artipie/npm-proxy-adapter)
[![Maven Central](https://img.shields.io/maven-central/v/com.artipie/npm-proxy-adapter.svg)](https://maven-badges.herokuapp.com/maven-central/com.artipie/npm-proxy-adapter)
[![PDD status](http://www.0pdd.com/svg?name=yegor256/npm-files)](http://www.0pdd.com/p?name=yegor256/npm-files)

# npm-remote-adapter

## Usage
This is the dependency you need:

```xml
<dependency>
  <groupId>com.artipie</groupId>
  <artifactId>npm-remote-adapter</artifactId>
  <version>[...]</version>
</dependency>
```

## How it works?

After initial setup of the proxy repository, user can specify it to work with NPM.
User call `npm install --registry=https://artipie.com/npm-remote-registry <package_name>` 
or setup default registry with `npm set registry https://artipie.com/npm-remote-registry` 
command. NPM sends HTTP requests to specified URL and adapter processes them. It proxies 
request to the remote repository previously setup. After getting artifact, it caches 
received data and transform before sending it back to client. The next request will not 
require remote repository call - the cached data will be used.

## Technical details

[Public NPM registry API ](https://github.com/npm/registry/blob/master/docs/REGISTRY-API.md) 
describes 6 methods. And one more method is called by `npm` client to get
security audit information. We will support just one in the beginning: get package by name.

### Get package by name method description
Receive request from `npm` client (`{package}` can be compound):
```http request
GET /path-to-repository/{package}
connection: keep-alive
user-agent: npm/6.14.3 node/v10.15.2 linux x64
npm-in-ci: false
npm-scope: 
npm-session: 439998791e27a7b1
referer: install
pacote-req-type: packument
pacote-pkg-id: registry:minimalistic-assert
accept: application/vnd.npm.install-v1+json; q=1.0, application/json; q=0.8, */*
accept-encoding: gzip,deflate
Host: artipie.com
```
At first, we try to get `{package}/package.json` file in the storage. If there is no
such file, we send request from `npm-remote-adapter` to remote registry:
```http request
GET /{package} HTTP/1.1
Host: registry.npmjs.org
Connection: Keep-Alive
User-Agent: Artipie/1.0.0
Accept-Encoding: gzip,deflate
```
Than we handle response from remote registry. Process headers:
```http request
HTTP/1.1 200 OK
Date: Thu, 02 Apr 2020 11:32:00 GMT
Content-Type: application/vnd.npm.install-v1+json
Content-Length: 14128
Connection: keep-alive
Set-Cookie: __cfduid=d2738100c3ba76fc8be39a390b96a23891585827120; expires=Sat, 02-May-20 11:32:00 GMT; path=/; domain=.npmjs.org; HttpOnly; SameSite=Lax
CF-Ray: 57da3a0fe8ac759b-DME
Accept-Ranges: bytes
Age: 2276
Cache-Control: public, max-age=300
ETag: "c0003ba714ae6ff25985f2b2206a669e"
Last-Modified: Wed, 12 Feb 2020 20:05:40 GMT
Vary: accept-encoding, accept
CF-Cache-Status: HIT
Expect-CT: max-age=604800, report-uri="https://report-uri.cloudflare.com/cdn-cgi/beacon/expect-ct"
Server: cloudflare
```
`Last-Modified` header will be persisted to `{package}/metadata.json`. Than we process body.
Body is persisted to `{package}/package.json` but all `tarball` fields need to be modified. 
There are two options: re-write all links on load or re-write them dynamically on each
request. The second option is better (it allows to use reverse proxy, for example), but 
it creates an additional load.

After that we generate response from the `{package}/package.json` and 
`{package}/metadata.json`. We replace placeholders in the `tarball` fields with
actual Artipie repository address and send the following headers:
```http request
HTTP/1.1 200 OK
Date: Thu, 02 Apr 2020 13:54:30 GMT
Server: Artipie/1.0.0
Connection: Keep-Alive
Content-Type: application/json
Content-Length: 14128
Last-Modified: Thu, 12 Mar 2020 18:49:03 GMT
```
where `Last-Modified` header is taken from `metadata.json`.

### Get tarball method description
It's the simplified case of the Get package call. We don't need to perform processing 
of the received data. Just need to put tarball in our storage and generates metadata.
Metadata filename is tarball filename with `.metadata` suffix.

Artipie determines the call type (package / tarball) by URL pattern:
* /{package} - Get package call;
* /{package}/-/{package}-{version}.tgz - Get tarball call.

One more thing to keep in mind - it's not required to have existing package metadata 
to process this call.

### Package/tarball not found
If adapter is unable to find the package neither it's own storage, not remote registry
it returns HTTP 404 answer:
```http request
HTTP/1.1 404 Not Found
Date: Thu, 02 Apr 2020 13:54:30 GMT
Server: Artipie/1.0.0
Connection: Keep-Alive
Content-Type: application/json
Content-Length: 21

{"error":"Not found"}
```

## Further improvements

All proposed features are subject to discuss. All of them are optional and can be 
implemented later. 

### Artifact package TTL

NPM first read package metadata. As been noticed before, remote adapter caches the response.
But when new artifact version will be uploaded to remote repository, metadata will be changed.
So proxy repository must reload metadata. It should either no cache metadata responses or
use expiring cache with customizable TTL for it.

### Artifact binaries TTL

Here is the opposite situation with metadata. Binaries usually can not be changed. But
"usually" does not mean "always". There are some conditions under which binaries can be
changed too. For example, some CI/CD flows can try to override the same artifact version
during development. In this case, make sense to manage artifact's binaries cache TTL too.

### Negative TTL

Let's assume that user calls `npm install foo-package`. And `foo-package` does not exist
at this time. We can cache this response and when user retries the call, we will send
response without remote repository interaction.

### Remote repository unavailability

Naive approach to proxy adapter - to call remote repository in synchronous way. Requests
themselves can be handled asynchronously, but whole interaction is synchronous. It means
that the only way to know about remote repository unavailability is to wait until it called.
The alternative is creation of asynchronous health-checker. Or, maybe, asynchronous task
that starts after first failed attempt to interact with repository. 

### Pre-fetching latest version

If NPM requests artifact in the first time and adapter does not find the package in 
the cache, it will download metadata from the remote repository. Then adapter sends
response to NPM and NPM create new requests to download specific version. But in the most
cases, it will be the latest version. And we can start to download it before NPM client
will actually request it.

## How to contribute

Fork repository, make changes, send us a pull request. We will review
your changes and apply them to the `master` branch shortly, provided
they don't violate our quality standards. To avoid frustration, before
sending us your pull request please run full Maven build:

```
$ mvn clean install -Pqulice
```

To avoid build errors use Maven 3.2+.