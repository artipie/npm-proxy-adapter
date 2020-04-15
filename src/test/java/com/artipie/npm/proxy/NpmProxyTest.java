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
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Test NPM Proxy works.
 *
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@ExtendWith(VertxExtension.class)
final class NpmProxyTest {
    /**
     * Port to listen for Mock Server.
     */
    private static final int DEF_PORT = 12_321;

    /**
     * Last modified date for both package and asset.
     */
    private static final String DEF_LAST_MODIFIED = "Tue, 24 Mar 2020 12:15:16 GMT";

    /**
     * Asset Content-Type.
     */
    private static final String DEF_CONTENT_TYPE = "application/octet-stream";

    /**
     * Assert content.
     */
    private static final String DEF_CONTENT = "foobar";

    /**
     * NPM Proxy instance.
     */
    private NpmProxy npm;

    /**
     * NPM Proxy storage.
     */
    private Storage storage;

    /**
     * Client Vertx instance.
     */
    private Vertx cvertx;

    @Test
    public void getsPackage(final VertxTestContext context)
        throws ParseException, IOException, JSONException {
        final NpmPackage pkg = this.npm.getPackage("asdas").blockingGet();
        context.completeNow();
        MatcherAssert.assertThat("Package is null", pkg != null);
        MatcherAssert.assertThat(
            pkg.lastModified(),
            new IsEqual<>(NpmProxyTest.DEF_LAST_MODIFIED)
        );
        final BlockingStorage bstorage = new BlockingStorage(this.storage);
        final String cached = new String(
            bstorage.value(new Key.From("asdas/package.json")),
            StandardCharsets.UTF_8
        );
        MatcherAssert.assertThat(
            "Package content is null",
            cached != null
        );
        JSONAssert.assertEquals(
            IOUtils.resourceToString(
                "/cached.json",
                StandardCharsets.UTF_8
            ),
            cached,
            true
        );
        MatcherAssert.assertThat(pkg.data(), new IsEqual<>(cached));
        final String metadata = new String(
            bstorage.value(new Key.From("asdas/package.metadata")),
            StandardCharsets.UTF_8
        );
        MatcherAssert.assertThat("Metadata is null", metadata != null);
        final JSONObject json = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE)
            .parse(metadata, JSONObject.class);
        MatcherAssert.assertThat(
            json.getAsString("last-modified"),
            new IsEqual<>(NpmProxyTest.DEF_LAST_MODIFIED)
        );
    }

    @Test
    public void cannotFindPackage(final VertxTestContext context) {
        MatcherAssert.assertThat(
            "Unexpected package found",
            this.npm.getPackage("not-found").blockingGet() == null
        );
        context.completeNow();
    }

    @Test
    public void getsAsset(final VertxTestContext context) throws ParseException {
        final NpmAsset asset = this.npm.getAsset("asdas/-/asdas-1.0.0.tgz").blockingGet();
        context.completeNow();
        MatcherAssert.assertThat("Asset is null", asset != null);
        MatcherAssert.assertThat(
            new String(
                new Concatenation(asset.dataPublisher()).single().blockingGet().array(),
                StandardCharsets.UTF_8
            ),
            new IsEqual<>(NpmProxyTest.DEF_CONTENT)
        );
        MatcherAssert.assertThat(
            asset.lastModified(),
            new IsEqual<>(NpmProxyTest.DEF_LAST_MODIFIED)
        );
        MatcherAssert.assertThat(
            asset.contentType(),
            new IsEqual<>(NpmProxyTest.DEF_CONTENT_TYPE)
        );
        final BlockingStorage bstorage = new BlockingStorage(this.storage);
        final String content = new String(
            bstorage.value(new Key.From("asdas/-/asdas-1.0.0.tgz")),
            StandardCharsets.UTF_8
        );
        MatcherAssert.assertThat(NpmProxyTest.DEF_CONTENT, new IsEqual<>(content));
        final String metadata = new String(
            bstorage.value(new Key.From("asdas/-/asdas-1.0.0.tgz.metadata")),
            StandardCharsets.UTF_8
        );
        MatcherAssert.assertThat("Metadata is null", metadata != null);
        final JSONObject json = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE)
            .parse(metadata, JSONObject.class);
        MatcherAssert.assertThat(
            json.getAsString("last-modified"),
            new IsEqual<>(NpmProxyTest.DEF_LAST_MODIFIED)
        );
        MatcherAssert.assertThat(
            json.getAsString("content-type"),
            new IsEqual<>(NpmProxyTest.DEF_CONTENT_TYPE)
        );
    }

    @Test
    public void cannotFindAsset(final VertxTestContext context) {
        MatcherAssert.assertThat(
            "Unexpected asset found",
            this.npm.getAsset("not-found").blockingGet() == null
        );
        context.completeNow();
    }

    @BeforeEach
    void setUp(final Vertx svertx, final VertxTestContext context)
        throws IOException, InterruptedException {
        prepareServer(svertx, context);
        this.cvertx = Vertx.vertx();
        this.storage = new InMemoryStorage();
        this.npm = new NpmProxy(
            new NpmProxySettings(defaultConfig()), this.cvertx, this.storage
        );
        MatcherAssert.assertThat(
            "Server was not started",
            context.awaitCompletion(1, TimeUnit.SECONDS)
        );
    }

    @AfterEach
    void tearDown() {
        this.npm.close();
        this.cvertx.close();
    }

    private static HttpServer prepareServer(
        final Vertx vertx,
        final VertxTestContext context) throws IOException {
        final String original = IOUtils.resourceToString(
            "/original.json",
            StandardCharsets.UTF_8
        );
        return vertx.createHttpServer().requestHandler(
            req -> {
                if (req.path().equalsIgnoreCase("/asdas")) {
                    req.response()
                        .putHeader("Last-Modified", NpmProxyTest.DEF_LAST_MODIFIED)
                        .end(original);
                } else if (req.path().equalsIgnoreCase("/asdas/-/asdas-1.0.0.tgz")) {
                    req.response()
                        .putHeader("Last-Modified", NpmProxyTest.DEF_LAST_MODIFIED)
                        .putHeader("Content-Type", NpmProxyTest.DEF_CONTENT_TYPE)
                        .end(NpmProxyTest.DEF_CONTENT);
                } else {
                    // @checkstyle MagicNumberCheck (1 line)
                    req.response().setStatusCode(404).end();
                }
            }
        ).listen(NpmProxyTest.DEF_PORT, context.completing());
    }

    private static BaseConfiguration defaultConfig() {
        final BaseConfiguration config = new BaseConfiguration();
        config.setProperty("port", NpmProxyTest.DEF_PORT);
        config.setProperty("host", "localhost");
        config.setProperty("ssl", false);
        return config;
    }

    /**
     * Scoped tests for prepared storage.
     *
     * @since 0.1
     */
    @Nested
    class CachedValues {
        @Test
        public void getsPackage() {
            final NpmPackage pkg = NpmProxyTest.this.npm.getPackage("asdas").blockingGet();
            MatcherAssert.assertThat("Package is null", pkg != null);
            MatcherAssert.assertThat(
                pkg.lastModified(),
                new IsEqual<>(NpmProxyTest.DEF_LAST_MODIFIED)
            );
        }

        @Test
        public void failsWhenGetPackage() {
            MatcherAssert.assertThat(
                "Unexpected package found",
                NpmProxyTest.this.npm.getPackage("not-found").blockingGet() == null
            );
        }

        @Test
        public void getsAsset() {
            final NpmAsset asset = NpmProxyTest.this.npm.getAsset("asdas/-/asdas-1.0.0.tgz")
                .blockingGet();
            MatcherAssert.assertThat("Asset is null", asset != null);
            MatcherAssert.assertThat(
                asset.lastModified(),
                new IsEqual<>(NpmProxyTest.DEF_LAST_MODIFIED)
            );
            MatcherAssert.assertThat(
                asset.contentType(),
                new IsEqual<>(NpmProxyTest.DEF_CONTENT_TYPE)
            );
        }

        @Test
        public void failsWhenGetAsset() {
            MatcherAssert.assertThat(
                "Unexpected asset found",
                NpmProxyTest.this.npm.getAsset("not-found").blockingGet() == null
            );
        }

        @BeforeEach
        void setUp(final Vertx svertx, final VertxTestContext context) {
            MatcherAssert.assertThat(
                "Package is null",
                NpmProxyTest.this.npm.getPackage("asdas").blockingGet() != null
            );
            MatcherAssert.assertThat(
                "Asset is null",
                NpmProxyTest.this.npm.getAsset("asdas/-/asdas-1.0.0.tgz").blockingGet() != null
            );
            context.completeNow();
            svertx.close();
        }
    }
}
