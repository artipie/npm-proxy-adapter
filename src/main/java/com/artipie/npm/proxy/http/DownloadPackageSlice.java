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
import com.artipie.npm.proxy.json.ClientContent;
import com.jcabi.log.Logger;
import hu.akarnokd.rxjava2.interop.SingleInterop;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import org.cactoos.list.ListOf;
import org.cactoos.map.MapEntry;
import org.reactivestreams.Publisher;

/**
 * HTTP slice for download package requests.
 * @since 0.1
 * @checkstyle ReturnCountCheck (200 lines)
 * @checkstyle ClassDataAbstractionCouplingCheck (200 lines)
 */
public final class DownloadPackageSlice implements Slice {
    /**
     * Download package request URL regexp pattern.
     */
    public static final Pattern PATH_PATTERN = Pattern.compile("^/(((?!/-/).)+)$");

    /**
     * NPM Proxy facade.
     */
    private final NpmProxy npm;

    /**
     * Ctor.
     *
     * @param npm NPM Proxy facade
     */
    public DownloadPackageSlice(final NpmProxy npm) {
        this.npm = npm;
    }

    @Override
    @SuppressWarnings("PMD.OnlyOneReturn")
    public Response response(final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        final Matcher matcher = DownloadPackageSlice.PATH_PATTERN
            .matcher(new RequestLineFrom(line).uri().getPath());
        if (!matcher.matches()) {
            return new RsNotFound();
        }
        final String name = matcher.group(1);
        Logger.debug(DownloadPackageSlice.class, "Determined package name is: %s", name);
        return new AsyncResponse(
            this.npm.getPackage(name)
                .map(
                    pkg -> (Response) new RsWithHeaders(
                        new RsWithBody(
                            new RsWithStatus(RsStatus.OK),
                            new Content.From(
                                DownloadPackageSlice.clientFormat(pkg.content(), headers)
                                    .getBytes()
                            )
                        ),
                        new ListOf<Map.Entry<String, String>>(
                            new MapEntry<>("Content-Type", "application/json"),
                            new MapEntry<>("Last-Modified", pkg.lastModified())
                        )
                    )
                ).toSingle(new RsNotFound())
                .to(SingleInterop.get())
        );
    }

    /**
     * Transform internal package format for external clients.
     * @param data Internal package data
     * @param headers Request headers
     * @return External client package
     */
    private static String clientFormat(final String data,
        final Iterable<Map.Entry<String, String>> headers) {
        final String host = StreamSupport.stream(headers.spliterator(), false)
            .filter(e -> e.getKey().equalsIgnoreCase("Host"))
            .findAny().orElseThrow(
                () -> new RuntimeException("Could not find Host header in request")
            ).getValue();
        return new ClientContent(data, String.format("http://%s", host)).value();
    }
}
