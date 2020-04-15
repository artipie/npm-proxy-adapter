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
package com.artipie.npm.proxy.http;

import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.SliceSimple;
import com.artipie.npm.proxy.NpmProxy;
import java.nio.ByteBuffer;
import java.util.Map;
import org.reactivestreams.Publisher;

/**
 * Main HTTP slice NPM Proxy adapter.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (100 lines)
 */
public final class NpmProxySlice implements Slice {
    /**
     * Route.
     */
    private final SliceRoute route;

    /**
     * Ctor.
     *
     * @param npm NPM Proxy facade
     */
    public NpmProxySlice(final NpmProxy npm) {
        this.route = new SliceRoute(
            new SliceRoute.Path(
                new RtRule.Multiple(
                    new RtRule.ByMethod(RqMethod.GET),
                    new RtRule.ByPath(DownloadPackageSlice.PATH_PATTERN)
                ),
                new DownloadPackageSlice(npm)
            ),
            new SliceRoute.Path(
                new RtRule.Multiple(
                    new RtRule.ByMethod(RqMethod.GET),
                    new RtRule.ByPath(DownloadAssetSlice.PATH_PATTERN)
                ),
                new DownloadAssetSlice(npm)
            ),
            new SliceRoute.Path(
                RtRule.FALLBACK,
                new SliceSimple(
                    new RsNotFound()
                )
            )
        );
    }

    @Override
    public Response response(final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        return this.route.response(line, headers, body);
    }
}
