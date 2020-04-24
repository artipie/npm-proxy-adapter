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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * NPM Proxy storage test.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class RxNpmProxyStorageTest {
    /**
     * Last modified date for both package and asset.
     */
    private static final String LAST_MODIFIED = "Tue, 24 Mar 2020 12:15:16 GMT";

    /**
     * Last updated date for package (datetime).
     */
    private static final OffsetDateTime LAST_UPDATED = OffsetDateTime.of(
        LocalDateTime.of(2020, Month.APRIL, 24, 12, 15, 16, 123_456_789),
        ZoneOffset.UTC
    );

    /**
     * Last updated date for package (string).
     */
    private static final String LAST_UPDATED_STR = "2020-04-24T12:15:16.123456789Z";

    /**
     * Asset Content-Type.
     */
    private static final String DEF_CONTENT_TYPE = "application/octet-stream";

    /**
     * Assert content.
     */
    private static final String DEF_CONTENT = "foobar";

    /**
     * NPM Proxy Storage.
     */
    private NpmProxyStorage storage;

    /**
     * Underlying storage.
     */
    private Storage delegate;

    @Test
    public void savesPackage() throws IOException, ExecutionException, InterruptedException {
        this.doSavePackage("asdas", RxNpmProxyStorageTest.LAST_UPDATED);
        MatcherAssert.assertThat(
            new String(
                new Concatenation(
                    this.delegate.value(new Key.From("asdas/package.json")).get()
                ).single().blockingGet().array(),
                StandardCharsets.UTF_8
            ),
            new IsEqual<>(RxNpmProxyStorageTest.readContent())
        );
        final String metadata = new String(
            new Concatenation(
                this.delegate.value(new Key.From("asdas/package.metadata")).get()
            ).single().blockingGet().array(),
            StandardCharsets.UTF_8
        );
        final JsonObject json = new JsonObject(metadata);
        MatcherAssert.assertThat(
            json.getString("last-modified"),
            new IsEqual<>(RxNpmProxyStorageTest.LAST_MODIFIED)
        );
        MatcherAssert.assertThat(
            new JsonObject(metadata).getString("last-updated"),
            new IsEqual<>(RxNpmProxyStorageTest.LAST_UPDATED_STR)
        );
    }

    @Test
    public void savesAsset() throws ExecutionException, InterruptedException {
        final String path = "asdas/-/asdas-1.0.0.tgz";
        this.doSaveAsset(path);
        MatcherAssert.assertThat(
            new String(
                new Concatenation(
                    this.delegate.value(new Key.From(path)).get()
                ).single().blockingGet().array(),
                StandardCharsets.UTF_8
            ),
            new IsEqual<>(RxNpmProxyStorageTest.DEF_CONTENT)
        );
        final String metadata = new String(
            new Concatenation(
                this.delegate.value(new Key.From("asdas/-/asdas-1.0.0.tgz.metadata")).get()
            ).single().blockingGet().array(),
            StandardCharsets.UTF_8
        );
        final JsonObject json = new JsonObject(metadata);
        MatcherAssert.assertThat(
            json.getString("last-modified"),
            new IsEqual<>(RxNpmProxyStorageTest.LAST_MODIFIED)
        );
        MatcherAssert.assertThat(
            json.getString("content-type"),
            new IsEqual<>(RxNpmProxyStorageTest.DEF_CONTENT_TYPE)
        );
    }

    @Test
    public void loadsPackage() throws IOException {
        final String name = "asdas";
        this.doSavePackage(name, RxNpmProxyStorageTest.LAST_UPDATED);
        final NpmPackage pkg = this.storage.getPackage(name).blockingGet();
        MatcherAssert.assertThat(
            pkg.name(),
            new IsEqual<>(name)
        );
        MatcherAssert.assertThat(
            pkg.content(),
            new IsEqual<>(RxNpmProxyStorageTest.readContent())
        );
        MatcherAssert.assertThat(
            pkg.lastModified(),
            new IsEqual<>(RxNpmProxyStorageTest.LAST_MODIFIED)
        );
        MatcherAssert.assertThat(
            pkg.lastUpdated(),
            new IsEqual<>(RxNpmProxyStorageTest.LAST_UPDATED)
        );
    }

    @Test
    public void loadsAsset() {
        final String path = "asdas/-/asdas-1.0.0.tgz";
        this.doSaveAsset(path);
        final NpmAsset asset = this.storage.getAsset(path).blockingGet();
        MatcherAssert.assertThat(
            asset.path(),
            new IsEqual<>(path)
        );
        MatcherAssert.assertThat(
            new String(
                new Concatenation(asset.dataPublisher()).single().blockingGet().array(),
                StandardCharsets.UTF_8
            ),
            new IsEqual<>(RxNpmProxyStorageTest.DEF_CONTENT)
        );
        MatcherAssert.assertThat(
            asset.lastModified(),
            new IsEqual<>(RxNpmProxyStorageTest.LAST_MODIFIED)
        );
        MatcherAssert.assertThat(
            asset.contentType(),
            new IsEqual<>(RxNpmProxyStorageTest.DEF_CONTENT_TYPE)
        );
    }

    @Test
    public void failsToLoadPackage() {
        MatcherAssert.assertThat(
            "Unexpected package found",
            this.storage.getPackage("not-found").isEmpty().blockingGet()
        );
    }

    @Test
    public void failsToLoadAsset() {
        MatcherAssert.assertThat(
            "Unexpected package asset",
            this.storage.getAsset("not-found").isEmpty().blockingGet()
        );
    }

    @BeforeEach
    void setUp() {
        this.delegate = new InMemoryStorage();
        this.storage = new RxNpmProxyStorage(new RxStorageWrapper(this.delegate));
    }

    private void doSavePackage(final String name, final OffsetDateTime updated)
        throws IOException {
        this.storage.save(
            new NpmPackage(
                name,
                RxNpmProxyStorageTest.readContent(),
                RxNpmProxyStorageTest.LAST_MODIFIED,
                updated
            )
        ).blockingAwait();
    }

    private void doSaveAsset(final String path) {
        this.storage.save(
            new NpmAsset(
                path,
                new Content.From(RxNpmProxyStorageTest.DEF_CONTENT.getBytes()),
                RxNpmProxyStorageTest.LAST_MODIFIED,
                RxNpmProxyStorageTest.DEF_CONTENT_TYPE
           )
        ).blockingAwait();
    }

    private static String readContent() throws IOException {
        return IOUtils.resourceToString(
            "/json/cached.json",
            StandardCharsets.UTF_8
        );
    }
}
