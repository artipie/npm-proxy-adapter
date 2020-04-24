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

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.artipie.asto.Concatenation;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Test NPM Proxy works.
 *
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class NpmProxyTest {
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
     * Vertx instance.
     */
    private static Vertx vertx;

    /**
     * NPM Proxy instance.
     */
    private NpmProxy npm;

    /**
     * Http Server instance.
     */
    private HttpServer server;

    @Test
    public void getsPackage() throws IOException, JSONException {
        final String name = "asdas";
        final NpmPackage pkg = this.npm.getPackage(name).blockingGet();
        this.validatePackage(pkg, name);
    }

    @Test
    public void cannotFindPackage() {
        final Boolean empty = this.npm.getPackage("not-found").isEmpty().blockingGet();
        MatcherAssert.assertThat("Unexpected package found", empty);
    }

    @Test
    public void getsAsset() {
        final String path = "asdas/-/asdas-1.0.0.tgz";
        final NpmAsset asset = this.npm.getAsset(path).blockingGet();
        this.validateAsset(asset, path);
    }

    @Test
    public void cannotFindAsset() {
        final Boolean empty = this.npm.getAsset("not-found").isEmpty().blockingGet();
        MatcherAssert.assertThat("Unexpected asset found", empty);
    }

    @BeforeEach
    void setUp()
        throws IOException, InterruptedException {
        final int port = this.rndPort();
        this.server = prepareServer(port);
        final YamlMapping yaml = Yaml.createYamlMappingBuilder()
            .add("remote-url", String.format("http://localhost:%d", port))
            .build();
        this.npm = new NpmProxy(
            new NpmProxyConfig(yaml),
            NpmProxyTest.vertx,
            new InMemoryStorage()
        );
    }

    @AfterEach
    void tearDown() {
        this.npm.close();
        this.server.close();
    }

    @BeforeAll
    static void prepare() {
        NpmProxyTest.vertx = Vertx.vertx();
    }

    @AfterAll
    static void cleanup() {
        NpmProxyTest.vertx.close();
    }

    private int rndPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void validatePackage(final NpmPackage pkg, final String name)
        throws IOException, JSONException {
        MatcherAssert.assertThat("Package is null", pkg != null);
        MatcherAssert.assertThat(
            pkg.name(),
            new IsEqual<>(name)
        );
        MatcherAssert.assertThat(
            pkg.lastModified(),
            new IsEqual<>(NpmProxyTest.DEF_LAST_MODIFIED)
        );
        JSONAssert.assertEquals(
            IOUtils.resourceToString("/json/cached.json", StandardCharsets.UTF_8),
            pkg.content(),
            true
        );
        MatcherAssert.assertThat(
            pkg.lastModified(),
            new IsEqual<>(NpmProxyTest.DEF_LAST_MODIFIED)
        );
    }

    private void validateAsset(final NpmAsset asset, final String path) {
        MatcherAssert.assertThat("Asset is null", asset != null);
        MatcherAssert.assertThat(
            asset.path(),
            new IsEqual<>(path)
        );
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
    }

    private static HttpServer prepareServer(final int port)
        throws IOException, InterruptedException {
        final String original = IOUtils.resourceToString(
            "/json/original.json",
            StandardCharsets.UTF_8
        );
        final CountDownLatch latch = new CountDownLatch(1);
        final HttpServer server = vertx.createHttpServer().requestHandler(
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
        ).listen(port, unused -> latch.countDown());
        latch.await();
        return server;
    }

    /**
     * Scoped tests for prepared storage.
     *
     * @since 0.1
     */
    @Nested
    class CachedValues {
        @Test
        public void getsPackage() throws IOException, JSONException {
            final String name = "asdas";
            final NpmPackage pkg = NpmProxyTest.this.npm.getPackage(name).blockingGet();
            MatcherAssert.assertThat("Package is null", pkg != null);
            NpmProxyTest.this.validatePackage(pkg, name);
        }

        @Test
        public void failsWhenGetPackage() {
            MatcherAssert.assertThat(
                "Unexpected package found",
                NpmProxyTest.this.npm.getPackage("not-found").isEmpty().blockingGet()
            );
        }

        @Test
        public void getsAsset() {
            final String path = "asdas/-/asdas-1.0.0.tgz";
            final NpmAsset asset = NpmProxyTest.this.npm.getAsset(path).blockingGet();
            NpmProxyTest.this.validateAsset(asset, path);
        }

        @Test
        public void failsWhenGetAsset() {
            MatcherAssert.assertThat(
                "Unexpected asset found",
                NpmProxyTest.this.npm.getAsset("not-found").isEmpty().blockingGet()
            );
        }

        @BeforeEach
        void setUp() throws InterruptedException {
            MatcherAssert.assertThat(
                "Package is null",
                !NpmProxyTest.this.npm.getPackage("asdas").isEmpty().blockingGet()
            );
            MatcherAssert.assertThat(
                "Asset is null",
                !NpmProxyTest.this.npm.getAsset("asdas/-/asdas-1.0.0.tgz").isEmpty().blockingGet()
            );
            final CountDownLatch latch = new CountDownLatch(1);
            NpmProxyTest.this.server.close(unused -> latch.countDown());
            latch.await();
        }
    }
}
