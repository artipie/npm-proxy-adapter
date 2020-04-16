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

/**
 * NPM Package.
 * @since 0.1
 */
public final class NpmPackage {
    /**
     * JSON data.
     */
    private final String content;

    /**
     * Last modified date.
     */
    private final String modified;

    /**
     * Ctor.
     * @param content JSON data
     * @param modified Last modified date
     */
    public NpmPackage(final String content, final String modified) {
        this.content = content;
        this.modified = modified;
    }

    /**
     * Get package JSON.
     * @return Package JSON
     */
    public String data() {
        return this.content;
    }

    /**
     * Get last modified date.
     * @return Last modified date
     */
    public String lastModified() {
        return this.modified;
    }
}
