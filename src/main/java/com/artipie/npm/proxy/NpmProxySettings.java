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

import org.apache.commons.configuration2.Configuration;

/**
 * NPM Proxy settings.
 * @since 0.1
 */
public class NpmProxySettings {
    /**
     * The delegate configuration.
     */
    private final Configuration delegate;

    /**
     * Ctor.
     * @param delegate Delegate configuration
     */
    NpmProxySettings(final Configuration delegate) {
        this.delegate = delegate;
    }

    /**
     * SSL enabled or disabled on the remote repository.
     * @return SSL status of the remote repository
     * @todo #1:30m Connection URL should be represented by one property in the underlying config.
     *  Replace ssl, host, port and path props with one - url.
     *  Web clients should used getAbs method with it
     */
    public boolean ssl() {
        return this.delegate.getBoolean("ssl");
    }

    /**
     * Remote repository host.
     * @return Remote repository host
     */
    public String host() {
        return this.delegate.getString("host");
    }

    /**
     * Remote repository port.
     * @return Remote repository port
     */
    public int port() {
        return this.delegate.getInt("port");
    }

    /**
     * Remote repository path for the NPM data.
     * @return Remote repository path for the NPM data
     */
    public String path() {
        return this.delegate.getString("path");
    }
}
