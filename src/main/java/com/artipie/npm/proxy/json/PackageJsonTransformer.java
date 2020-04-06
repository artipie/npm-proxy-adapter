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
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minidev.json.JSONArray;

/**
 * JSON transformation utility for package body.
 * @since 0.1
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class PackageJsonTransformer {
    /**
     * Placeholder for asset links base URL.
     */
    public static final String PLACEHOLDER = "${artipie.npm_proxy_registry}";

    /**
     * Regexp pattern for asset links.
     */
    private static final String REF_PATTERN = "^(.+)/(%s/-/.+)$";

    /**
     * Ctor.
     */
    private PackageJsonTransformer() {
    }

    /**
     * Transforms original package data to internal adapter representation.
     * @param data Original JSON data
     * @param pkg Package name
     * @return Transformed package JSON
     */
    public static String transformOriginal(final String data, final String pkg) {
        return transformAssetRefs(data, pkg, PackageJsonTransformer::transformOriginalAssetRef);
    }

    /**
     * Transforms internal adapter representation for clients.
     * @param data Internal package JSON
     * @param url Base URL where adapter is published
     * @return Transformed package JSON
     */
    public static String transformCached(final String data, final String url) {
        return transformAssetRefs(data, url, PackageJsonTransformer::transformCachedAssetRef);
    }

    /**
     * Generic method for tranforming package JSON.
     * @param data JSON data
     * @param param Argument of the replacement function
     * @param replacement Replacement function
     * @return Transformed JSON
     */
    private static String transformAssetRefs(
        final String data,
        final String param,
        final BiFunction<String, String, String> replacement) {
        final DocumentContext json = JsonPath.parse(data);
        final Configuration conf = Configuration.builder().options(Option.AS_PATH_LIST).build();
        final DocumentContext ctx = JsonPath.parse(data, conf);
        ctx.read("$.versions.[*].dist.tarball", JSONArray.class).stream()
            .map(String.class::cast).forEach(
                path -> {
                    final String asset = json.read(path);
                    json.set(path, replacement.apply(asset, param));
                }
        );
        return json.jsonString();
    }

    /**
     * Generate internal asset link from original.
     * @param ref Original asset reference
     * @param pkg Package name
     * @return Asset reference for internal representation
     * @checkstyle ReturnCountCheck (12 lines)
     */
    @SuppressWarnings("PMD.OnlyOneReturn")
    private static String transformOriginalAssetRef(final String ref, final String pkg) {
        final Pattern pattern = Pattern.compile(
            String.format(PackageJsonTransformer.REF_PATTERN, pkg)
        );
        final Matcher matcher = pattern.matcher(ref);
        if (matcher.matches()) {
            return String.format("%s/%s", PackageJsonTransformer.PLACEHOLDER, matcher.group(2));
        } else {
            return ref;
        }
    }

    /**
     * Generate client asset link from internal.
     * @param ref Internal asset reference
     * @param url Base URL where adapter is published
     * @return Asset reference for clients
     */
    private static String transformCachedAssetRef(final String ref, final String url) {
        return ref.replace(PackageJsonTransformer.PLACEHOLDER, url);
    }
}
