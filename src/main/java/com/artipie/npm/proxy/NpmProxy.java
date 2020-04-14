/*
 * MIT License
 *
 * Copyright (c) 2020 artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.npm.proxy;

import com.artipie.asto.Concatenation;
import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.fs.RxFile;
import com.artipie.asto.rx.RxStorage;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.npm.proxy.json.CachedPackage;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import com.jcabi.log.Logger;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Single;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.file.AsyncFile;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import net.minidev.json.parser.JSONParser;
import org.apache.commons.lang3.StringUtils;

/**
 * NPM Proxy.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class NpmProxy {
    /**
     * Default connection timeout to remote repo (in millis).
     */
    private static final int CONNECT_TIMEOUT = 2_000;

    /**
     * Default request timeout to remote repo (in millis).
     */
    private static final int REQUEST_TIMEOUT = 5_000;

    /**
     * The settings.
     */
    private final NpmProxySettings settings;

    /**
     * The Vertx instance.
     */
    private final Vertx vertx;

    /**
     * The storage.
     */
    private final RxStorage storage;

    /**
     * Web client.
     */
    private final WebClient client;

    /**
     * Ctor.
     * @param settings Adapter settings
     * @param vertx Vertx instance
     * @param storage Adapter storage
     */
    public NpmProxy(final NpmProxySettings settings, final Vertx vertx, final Storage storage) {
        this.settings = settings;
        this.vertx = vertx;
        this.storage = new RxStorageWrapper(storage);
        this.client =  WebClient.create(vertx, NpmProxy.defaultWebClientOptions());
    }

    /**
     * Retrieve package metadata.
     * @param pkg Package name
     * @return Package metadata (cached or downloaded from remote repository)
     * @checkstyle ReturnCountCheck (42 lines)
     */
    public Maybe<NpmPackage> getPackage(final String pkg) {
        final Key key = new Key.From(pkg, "package.json");
        final RequestOptions request = this.buildRequest(pkg);
        return this.client.request(HttpMethod.GET, request)
            .timeout(NpmProxy.REQUEST_TIMEOUT)
            .rxSend()
            .flatMapMaybe(
                response -> {
                    //@checkstyle MagicNumberCheck (1 line)
                    if (response.statusCode() == 200) {
                        return Completable.concatArray(
                            this.storage.save(
                                key,
                                new Content.From(
                                    new CachedPackage(response.bodyAsString(), pkg).value()
                                        .getBytes(StandardCharsets.UTF_8)
                                )
                            ),
                            this.storage.save(
                                new Key.From(pkg, "package.metadata"),
                                new Content.From(
                                    NpmProxy.packageMetadata(response)
                                        .getBytes(StandardCharsets.UTF_8)
                              )
                            )
                        ).andThen(Maybe.defer(() -> this.readPackageFromStorage(pkg).toMaybe()));
                    } else {
                        return this.tryLocal(pkg);
                    }
                }
            ).onErrorResumeNext(
                throwable -> {
                    Logger.error(
                        NpmProxy.class,
                        "Error occurred when process get package call: %s",
                        throwable.getMessage()
                    );
                    return this.tryLocal(pkg);
                }
            );
    }

    /**
     * Retrieve asset.
     * @param path Asset path
     * @return Asset data (cached or downloaded from remote repository)
     * @checkstyle ReturnCountCheck (54 lines)
     */
    public Maybe<NpmAsset> getAsset(final String path) {
        final Key key = new Key.From(path);
        return this.storage.exists(key)
            .flatMapMaybe(
                exists -> {
                    if (exists) {
                        return this.readAssetFromStorage(path).toMaybe();
                    } else {
                        final RequestOptions request = this.buildRequest(path);
                        final File tmp = Files.createTempFile("npm-asset-", ".tmp").toFile();
                        final AsyncFile asyncfile = this.vertx.fileSystem().openBlocking(
                            tmp.getAbsolutePath(),
                            new OpenOptions().setSync(true).setTruncateExisting(true)
                        );
                        return this.client.request(HttpMethod.GET, request)
                            .as(BodyCodec.pipe(asyncfile))
                            .rxSend().flatMapMaybe(
                                response -> {
                                    // @checkstyle MagicNumberCheck (1 line)
                                    if (response.statusCode() == 200) {
                                        return Completable.concatArray(
                                            this.storage.save(
                                                key,
                                                new Content.From(
                                                    new RxFile(
                                                        tmp.toPath(),
                                                        this.vertx.fileSystem()
                                                    ).flow()
                                                )
                                            ),
                                            this.storage.save(
                                                new Key.From(String.format("%s.metadata", path)),
                                                new Content.From(
                                                    NpmProxy.assetMetadata(response)
                                                        .getBytes(StandardCharsets.UTF_8)
                                                )
                                            )
                                        ).andThen(
                                            Maybe.defer(
                                                () -> this.readAssetFromStorage(path).toMaybe()
                                            )
                                        );
                                    } else {
                                        return Maybe.empty();
                                    }
                                }
                            )
                            .onErrorResumeNext(Maybe.empty())
                            .doOnTerminate(tmp::delete);
                    }
                }
            );
    }

    /**
     * Close NPM Proxy adapter and underlying Web Client.
     */
    public void close() {
        this.client.close();
    }

    /**
     * Try to load package metadata from the cache.
     * @param pkg Package name
     * @return Package metadata if exists
     * @checkstyle ReturnCountCheck (13 lines)
     */
    private MaybeSource<? extends NpmPackage> tryLocal(final String pkg) {
        return this.storage.exists(new Key.From(String.format("%s/package.json", pkg)))
            .flatMapMaybe(
                exists -> {
                    Logger.debug(
                        NpmProxy.class,
                        "Cached package '%s' exists: %b",
                        pkg,
                        exists
                    );
                    if (exists) {
                        return this.readPackageFromStorage(pkg).toMaybe();
                    } else {
                        return Maybe.empty();
                    }
                }
            );
    }

    /**
     * Read package data from storage.
     * @param pkg Package name
     * @return Package metadata
     */
    private Single<NpmPackage> readPackageFromStorage(final String pkg) {
        return this.storage.value(new Key.From(pkg, "package.json"))
            .map(Concatenation::new).flatMap(Concatenation::single)
            .zipWith(
                this.storage.value(new Key.From(pkg, "package.metadata"))
                    .map(Concatenation::new).flatMap(Concatenation::single)
                    .map(metadata -> new String(metadata.array(), StandardCharsets.UTF_8))
                    .map(
                        metadata -> new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE)
                            .parse(metadata, JSONObject.class)
                    ),
                (content, metadata) ->
                    new NpmPackage(
                        new String(content.array(), StandardCharsets.UTF_8),
                        metadata.getAsString("last-modified")
                    )
            );
    }

    /**
     * Read asset data from storage.
     * @param path Asset path
     * @return Asset data
     */
    private Single<NpmAsset> readAssetFromStorage(final String path) {
        return this.storage.value(new Key.From(path))
            .zipWith(
                this.storage.value(new Key.From(String.format("%s.metadata", path)))
                    .map(Concatenation::new).flatMap(Concatenation::single)
                    .map(metadata -> new String(metadata.array(), StandardCharsets.UTF_8))
                    .map(
                        metadata -> new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE)
                            .parse(metadata, JSONObject.class)
                    ),
                (content, metadata) ->
                    new NpmAsset(
                        content,
                        metadata.getAsString("last-modified"),
                        metadata.getAsString("content-type")
                    )
            );
    }

    /**
     * Build request to remote repository.
     * @param path Remote repository path
     * @return Request to execute
     */
    private RequestOptions buildRequest(final String path) {
        final RequestOptions request = new RequestOptions();
        final String uri;
        if (StringUtils.isEmpty(this.settings.path())) {
            uri = String.format("/%s", path);
        } else {
            uri = String.format("/%s/%s", this.settings.path(), path);
        }
        request.setURI(uri);
        request.setSsl(this.settings.ssl());
        request.setPort(this.settings.port());
        request.setHost(this.settings.host());
        return request;
    }

    /**
     * Generate additional package metadata (last modified date, etc).
     * @param response Remote repository response
     * @return Additional adapter metadata for package
     */
    private static String packageMetadata(final HttpResponse<Buffer> response) {
        final JSONObject json = new JSONObject();
        json.put("last-modified", response.getHeader("Last-Modified"));
        return json.toJSONString();
    }

    /**
     * Generate additional asset metadata (last modified date, etc).
     * @param response Remote repository response
     * @return Additional adapter metadata for package
     */
    private static String assetMetadata(final HttpResponse<Void> response) {
        final JSONObject json = new JSONObject();
        json.put("last-modified", response.getHeader("Last-Modified"));
        json.put("content-type", JSONValue.parse(response.getHeader("Content-Type"), String.class));
        return json.toJSONString();
    }

    /**
     * Build default Web Client options.
     * @return Default Web Client options
     */
    private static WebClientOptions defaultWebClientOptions() {
        final WebClientOptions options = new WebClientOptions();
        options.setKeepAlive(true);
        options.setUserAgent("Artipie");
        options.setConnectTimeout(NpmProxy.CONNECT_TIMEOUT);
        return options;
    }
}
