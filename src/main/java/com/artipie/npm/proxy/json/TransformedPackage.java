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
package com.artipie.npm.proxy.json;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import net.minidev.json.JSONArray;

/**
 * Abstract package content representation that supports JSON transformation.
 *
 * @since 0.1
 */
public abstract class TransformedPackage {
    /**
     * Placeholder for asset links base URL.
     */
    static final String PLACEHOLDER = "${artipie.npm_proxy_registry}";

    /**
     * Original package content.
     */
    private final String content;

    /**
     * Ctor.
     * @param content Package content to be transformed
     */
    public TransformedPackage(final String content) {
        this.content = content;
    }

    /**
     * Returns transformed package content as String.
     * @return Transformed package content
     */
    public String value() {
        return this.transformAssetRefs();
    }

    /**
     * Transforms asset references.
     * @param ref Original asset reference
     * @return Transformed asset reference
     */
    abstract String transformRef(String ref);

    /**
     * Transforms package JSON.
     * @return Transformed JSON
     */
    private String transformAssetRefs() {
        final DocumentContext json = JsonPath.parse(this.content);
        final Configuration conf = Configuration.builder().options(Option.AS_PATH_LIST).build();
        final DocumentContext ctx = JsonPath.parse(this.content, conf);
        ctx.read("$.versions.[*].dist.tarball", JSONArray.class).stream()
            .map(String.class::cast).forEach(
                path -> {
                    final String asset = json.read(path);
                    json.set(path, this.transformRef(asset));
                }
        );
        return json.jsonString();
    }
}
