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
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithBody;
import com.artipie.http.rs.RsWithHeaders;
import com.artipie.http.rs.RsWithStatus;
import java.util.Collections;
import org.cactoos.map.MapEntry;

/**
 * Utility class for adapter static responses.
 * @since 0.1
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class RsProvider {
    /**
     * Ctor.
     */
    private RsProvider() {
    }

    /**
     * Generate HTTP 404 Not found response.
     * @return HTTP 404 Not found response
     */
    public static Response notFound() {
        return new RsWithHeaders(
            new RsWithBody(
                new RsWithStatus(RsStatus.NOT_FOUND),
                new Content.From("{\"error\" : \"not found\"}".getBytes())
            ),
            Collections.singleton(new MapEntry<>("Content-Type", "application/json"))
        );
    }
}
