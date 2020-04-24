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
import com.artipie.asto.rx.RxStorage;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;

/**
 * NPM Proxy storage implementation.
 * @since 0.1
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class NpmProxyStorageImpl implements NpmProxyStorage {
    /**
     * Underlying storage.
     */
    private final RxStorage storage;

    /**
     * Ctor.
     * @param storage Underlying storage
     */
    public NpmProxyStorageImpl(final RxStorage storage) {
        this.storage = storage;
    }

    @Override
    public Completable save(final NpmPackage pkg) {
        final Key key = new Key.From(pkg.name(), "package.json");
        return Completable.concatArray(
            this.storage.save(
                key,
                new Content.From(pkg.content().getBytes(StandardCharsets.UTF_8))
            ),
            this.storage.save(
                new Key.From(pkg.name(), "package.metadata"),
                new Content.From(
                    NpmProxyStorageImpl.packageMetadata(pkg).getBytes(StandardCharsets.UTF_8)
                )
            )
        );
    }

    @Override
    public Completable save(final NpmAsset asset) {
        final Key key = new Key.From(asset.path());
        return Completable.concatArray(
            this.storage.save(
                key,
                new Content.From(asset.dataPublisher())
            ),
            this.storage.save(
                new Key.From(
                    String.format("%s.metadata", asset.path())
                ),
                new Content.From(
                    NpmProxyStorageImpl.assetMetadata(asset).getBytes(StandardCharsets.UTF_8)
                )
            )
        );
    }

    @Override
    // @checkstyle ReturnCountCheck (15 lines)
    public Maybe<NpmPackage> getPackage(final String name) {
        return this.storage.exists(new Key.From(name, "package.json"))
            .flatMapMaybe(
                exists -> {
                    if (exists) {
                        return this.readPackage(name).toMaybe();
                    } else {
                        return Maybe.empty();
                    }
                }
            );
    }

    @Override
    // @checkstyle ReturnCountCheck (15 lines)
    public Maybe<NpmAsset> getAsset(final String path) {
        return this.storage.exists(new Key.From(path))
            .flatMapMaybe(
                exists -> {
                    if (exists) {
                        return this.readAsset(path).toMaybe();
                    } else {
                        return Maybe.empty();
                    }
                }
            );
    }

    /**
     * Read NPM package from storage.
     * @param name Package name
     * @return NPM package
     */
    private Single<NpmPackage> readPackage(final String name) {
        return this.storage.value(new Key.From(name, "package.json"))
            .map(Concatenation::new).flatMap(Concatenation::single)
            .zipWith(
                this.storage.value(new Key.From(name, "package.metadata"))
                    .map(Concatenation::new).flatMap(Concatenation::single)
                    .map(metadata -> new String(metadata.array(), StandardCharsets.UTF_8))
                    .map(JsonObject::new),
                (content, metadata) ->
                    new NpmPackage(
                        name,
                        new String(content.array(), StandardCharsets.UTF_8),
                        metadata.getString("last-modified")
                    )
                );
    }

    /**
     * Read NPM Asset from storage.
     * @param path Asset path
     * @return NPM asset
     */
    private Single<NpmAsset> readAsset(final String path) {
        return this.storage.value(new Key.From(path))
            .zipWith(
                this.storage.value(new Key.From(String.format("%s.metadata", path)))
                    .map(Concatenation::new).flatMap(Concatenation::single)
                    .map(metadata -> new String(metadata.array(), StandardCharsets.UTF_8))
                    .map(JsonObject::new),
                (content, metadata) ->
                    new NpmAsset(
                        path,
                        content,
                        metadata.getString("last-modified"),
                        metadata.getString("content-type")
                    )
            );
    }

    /**
     * Generate additional package metadata (last modified date, etc).
     * @param pkg NPM package
     * @return Additional adapter metadata for package
     */
    private static String packageMetadata(final NpmPackage pkg) {
        final JsonObject json = new JsonObject();
        json.put("last-modified", pkg.lastModified());
        return json.encode();
    }

    /**
     * Generate additional asset metadata (last modified date, etc).
     * @param asset NPM asset
     * @return Additional adapter metadata for package
     */
    private static String assetMetadata(final NpmAsset asset) {
        final JsonObject json = new JsonObject();
        json.put("last-modified", asset.lastModified());
        json.put("content-type", asset.contentType());
        return json.encode();
    }
}
