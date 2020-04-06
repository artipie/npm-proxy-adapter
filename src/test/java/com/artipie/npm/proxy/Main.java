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

import com.artipie.asto.Storage;
import com.artipie.asto.fs.FileStorage;
import com.artipie.npm.proxy.http.NpmProxySlice;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.configuration2.BaseConfiguration;

/**
 * Test application to run Pie w/ NPM Proxy.
 *
 * @since 0.1
 */
public final class Main {
    /**
     * Ctor.
     */
    private Main() {
    }

    /**
     * Main method of Java app.
     * @param args Command-line arguments
     * @throws IOException when storage error occurred
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static void main(final String[] args) throws IOException {
        final NpmProxySettings settings = Main.defaultSettings();
        final Vertx vertx = Vertx.vertx();
        final Storage storage = Main.getStorage(vertx);
        final NpmProxy npm = new NpmProxy(settings, vertx, storage);
        final NpmProxySlice slice = new NpmProxySlice(npm);
        // @checkstyle MagicNumberCheck (1 line)
        try (VertxSliceServer srv = new VertxSliceServer(vertx, slice, 9080)) {
            srv.start();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // @checkstyle MagicNumberCheck (1 line)
                    Thread.sleep(100);
                } catch (final InterruptedException iox) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Creates FileStorage in temporary dir.
     * @param vertx Vertx instance
     * @return File storage
     * @throws IOException when storage error occurred
     */
    private static Storage getStorage(final Vertx vertx) throws IOException {
        final Path dir = Files.createTempDirectory("npm-proxy-");
        dir.toFile().deleteOnExit();
        return new FileStorage(dir, vertx.fileSystem());
    }

    /**
     * Build pre-defined NPM Proxy settings.
     * @return NPM Proxy settings
     */
    private static NpmProxySettings defaultSettings() {
        final BaseConfiguration config = new BaseConfiguration();
        // @checkstyle MagicNumberCheck (1 line)
        config.setProperty("port", 443);
        config.setProperty("host", "registry.npmjs.org");
        config.setProperty("ssl", true);
        return new NpmProxySettings(config);
    }
}
