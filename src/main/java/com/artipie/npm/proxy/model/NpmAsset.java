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
package com.artipie.npm.proxy.model;

import java.nio.ByteBuffer;
import org.reactivestreams.Publisher;

/**
 * NPM Asset.
 * @since 0.1
 */
public final class NpmAsset {
    /**
     * Reactive publisher for asset content.
     */
    private final Publisher<ByteBuffer> content;

    /**
     * Last modified date.
     */
    private final String modified;

    /**
     * Original content type.
     */
    private final String ctype;

    /**
     * Ctor.
     * @param content Reactive publisher for asset content
     * @param modified Last modified date
     * @param ctype Original content type
     */
    public NpmAsset(final Publisher<ByteBuffer> content,
        final String modified,
        final String ctype) {
        this.content = content;
        this.modified = modified;
        this.ctype = ctype;
    }

    /**
     * Get reactive data publisher.
     * @return Data publisher
     */
    public Publisher<ByteBuffer> dataPublisher() {
        return this.content;
    }

    /**
     * Get last modified date.
     * @return Last modified date
     */
    public String lastModified() {
        return this.modified;
    }

    /**
     * Get original content type.
     * @return Original content type
     */
    public String contentType() {
        return this.ctype;
    }
}
