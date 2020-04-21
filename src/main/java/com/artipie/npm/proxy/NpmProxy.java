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

import com.artipie.asto.Storage;
import com.artipie.asto.fs.RxFile;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.npm.proxy.json.CachedContent;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import com.jcabi.log.Logger;
import io.reactivex.Maybe;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import java.io.File;
import java.nio.file.Files;

/**
 * NPM Proxy.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @todo #1:90m Extract storage logic into separate class
 *  Split this class to smaller parts. Storage logic can be extracted.
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
     * NPM Proxy config.
     */
    private final NpmProxyConfig config;

    /**
     * The Vertx instance.
     */
    private final Vertx vertx;

    /**
     * The storage.
     */
    private final NpmProxyStorage storage;

    /**
     * Web client.
     */
    private final WebClient client;

    /**
     * Ctor.
     * @param config NPM Proxy configuration
     * @param vertx Vertx instance
     * @param storage Adapter storage
     */
    public NpmProxy(final NpmProxyConfig config, final Vertx vertx, final Storage storage) {
        this.config = config;
        this.vertx = vertx;
        this.storage = new NpmProxyStorage(new RxStorageWrapper(storage));
        this.client =  WebClient.create(vertx, NpmProxy.defaultWebClientOptions());
    }

    /**
     * Retrieve package metadata.
     * @param pkg Package name
     * @return Package metadata (cached or downloaded from remote repository)
     * @checkstyle ReturnCountCheck (42 lines)
     */
    public Maybe<NpmPackage> getPackage(final String pkg) {
        return this.client.getAbs(String.format("%s/%s", this.config.url(), pkg))
            .timeout(NpmProxy.REQUEST_TIMEOUT)
            .rxSend()
            .flatMapMaybe(
                response -> {
                    //@checkstyle MagicNumberCheck (1 line)
                    if (response.statusCode() == 200) {
                        return this.storage.save(
                            new NpmPackage(
                                pkg,
                                new CachedContent(response.bodyAsString(), pkg).value(),
                                response.getHeader("Last-Modified")
                            )
                        ).andThen(Maybe.defer(() -> this.storage.getPackage(pkg)));
                    } else {
                        return this.storage.getPackage(pkg);
                    }
                }
            ).onErrorResumeNext(
                throwable -> {
                    Logger.error(
                        NpmProxy.class,
                        "Error occurred when process get package call: %s",
                        throwable.getMessage()
                    );
                    return this.storage.getPackage(pkg);
                }
            );
    }

    /**
     * Retrieve asset.
     * @param path Asset path
     * @return Asset data (cached or downloaded from remote repository)
     * @checkstyle ReturnCountCheck (43 lines)
     */
    public Maybe<NpmAsset> getAsset(final String path) {
        return this.storage.getAsset(path)
            .switchIfEmpty(
                Maybe.defer(
                    () -> {
                        final File tmp = Files.createTempFile("npm-asset-", ".tmp").toFile();
                        return this.vertx.fileSystem().rxOpen(
                            tmp.getAbsolutePath(),
                            new OpenOptions().setSync(true).setTruncateExisting(true)
                        ).flatMapMaybe(
                            asyncfile ->
                                this.client.getAbs(
                                    String.format("%s/%s", this.config.url(), path)
                                ).as(
                                    BodyCodec.pipe(asyncfile)
                                ).rxSend().flatMapMaybe(
                                    response -> {
                                        // @checkstyle MagicNumberCheck (1 line)
                                        if (response.statusCode() == 200) {
                                            return this.storage.save(
                                                new NpmAsset(
                                                    path,
                                                    new RxFile(
                                                        tmp.toPath(),
                                                        this.vertx.fileSystem()
                                                    ).flow(),
                                                    response.getHeader("Last-Modified"),
                                                    response.getHeader("Content-Type")
                                                )
                                            ).andThen(this.storage.getAsset(path));
                                        } else {
                                            return Maybe.empty();
                                        }
                                    }
                                ).onErrorResumeNext(
                                    Maybe.empty()
                                ).doOnTerminate(tmp::delete)
                        );
                    }
                )
            );
    }

    /**
     * Close NPM Proxy adapter and underlying Web Client.
     */
    public void close() {
        this.client.close();
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
