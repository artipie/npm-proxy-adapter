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

import com.artipie.asto.Content;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithBody;
import com.artipie.http.rs.RsWithHeaders;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.npm.proxy.NpmProxy;
import com.jcabi.log.Logger;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import org.cactoos.map.MapEntry;
import org.reactivestreams.Publisher;

/**
 * HTTP slice for download asset requests.
 * @since 0.1
 */
public final class DownloadAssetSlice implements Slice {
    /**
     * Download asset request URL regexp pattern.
     */
    public static final Pattern PATH_PATTERN = Pattern.compile("^(.+)/-/(.+)$");

    /**
     * NPM Proxy facade.
     */
    private final NpmProxy npm;

    /**
     * Ctor.
     *
     * @param npm NPM Proxy facade
     */
    public DownloadAssetSlice(final NpmProxy npm) {
        this.npm = npm;
    }

    @Override
    public Response response(final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        Logger.debug(
            DownloadPackageSlice.class,
            "Processing download asset request for query %s",
            line
        );
        final String path = new RequestLineFrom(line).uri().getPath().substring(1);
        Logger.debug(DownloadPackageSlice.class, "Determined asset path is: %s", path);
        return new AsyncResponse(
            this.npm.getAsset(path)
                .map(
                    asset -> (Response) new RsWithHeaders(
                        new RsWithBody(
                            new RsWithStatus(RsStatus.OK),
                            new Content.From(
                                asset.dataPublisher()
                            )
                        ),
                        Arrays.asList(
                            new MapEntry<>("Content-Type", asset.contentType()),
                            new MapEntry<>("Last-Modified", asset.lastModified())
                        )
                    )
                )
                .toSingle(RsProvider.notFound())
                .to(SingleInterop.get())
        );
    }
}
