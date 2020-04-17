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

import com.amihaiemil.eoyaml.Yaml;
import com.amihaiemil.eoyaml.YamlMapping;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.npm.proxy.http.NpmProxySlice;
import com.artipie.vertx.VertxSliceServer;
import com.google.common.net.MediaType;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;

/**
 * Integration test for NPM Proxy.
 *
 * It uses MockServer container to emulate Remote registry responses,
 * and Node container to run npm install command.
 *
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@org.testcontainers.junit.jupiter.Testcontainers
public final class NpmProxyITCase {
    /**
     * Vertx instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Port to listen for NPM Proxy adapter.
     */
    private static final int DEF_PORT = 9080;

    /**
     * Mock server test container.
     * @todo #1:120m Use real NPM repo instead of mocks.
     *  Take a look at https://github.com/verdaccio/verdaccio for example
     */
    @org.testcontainers.junit.jupiter.Container
    private final MockServerContainer mockctner = new MockServerContainer();

    /**
     * Node test container.
     */
    @org.testcontainers.junit.jupiter.Container
    private final NodeContainer npmcnter = new NodeContainer()
        .withCommand("tail", "-f", "/dev/null");

    /**
     * Vertx slice instance.
     */
    private VertxSliceServer srv;

    @Test
    public void installModule() throws IOException, InterruptedException {
        final MockServerClient mock = new MockServerClient(
            this.mockctner.getContainerIpAddress(),
            this.mockctner.getServerPort()
        );
        this.successPackage(mock);
        this.successAsset(mock);
        final Container.ExecResult result = this.npmcnter.execInContainer(
            "npm",
            "--registry",
            "http://host.testcontainers.internal:9080",
            "install",
            "asdas"
        );
        MatcherAssert.assertThat(
            result.getStdout(),
            new AllOf<>(
                Arrays.asList(
                    new StringContains("+ asdas@1.0.0"),
                    new StringContains("added 1 package")
                )
            )
        );
    }

    @Test
    public void packageNotFound() throws IOException, InterruptedException {
        final Container.ExecResult result = this.npmcnter.execInContainer(
            "npm",
            "--registry",
            "http://host.testcontainers.internal:9080",
            "install",
            "asdas"
        );
        MatcherAssert.assertThat(result.getExitCode(), new IsEqual<>(1));
        MatcherAssert.assertThat(
            result.getStderr(),
            new StringContains(
                "Not Found - GET http://host.testcontainers.internal:9080/asdas"
            )
        );
    }

    @Test
    public void assetNotFound() throws IOException, InterruptedException {
        final MockServerClient mock = new MockServerClient(
            this.mockctner.getContainerIpAddress(),
            this.mockctner.getServerPort()
        );
        this.successPackage(mock);
        final Container.ExecResult result = this.npmcnter.execInContainer(
            "npm",
            "--registry",
            "http://host.testcontainers.internal:9080",
            "install",
            "asdas"
        );
        MatcherAssert.assertThat(result.getExitCode(), new IsEqual<>(1));
        MatcherAssert.assertThat(
            result.getStderr(),
            new StringContains(
                "Not Found - GET http://host.testcontainers.internal:9080/asdas/-/asdas-1.0.0.tgz"
            )
        );
    }

    @BeforeEach
    void setUp() {
        final String address = this.mockctner.getContainerIpAddress();
        final Integer port = this.mockctner.getFirstMappedPort();
        final Storage storage = new InMemoryStorage();
        final YamlMapping yaml = Yaml.createYamlMappingBuilder()
            .add("remote-url", String.format("http://%s:%d", address, port))
            .build();
        final NpmProxy npm = new NpmProxy(
            new NpmProxyConfig(yaml),
            NpmProxyITCase.VERTX,
            storage
        );
        final NpmProxySlice slice = new NpmProxySlice(npm);
        this.srv = new VertxSliceServer(NpmProxyITCase.VERTX, slice, NpmProxyITCase.DEF_PORT);
        this.srv.start();
    }

    @AfterEach
    void tearDown() {
        this.srv.stop();
    }

    @BeforeAll
    static void prepare() {
        Testcontainers.exposeHostPorts(NpmProxyITCase.DEF_PORT);
    }

    @AfterAll
    static void finish() {
        NpmProxyITCase.VERTX.close();
    }

    private void successPackage(final MockServerClient mock) throws IOException {
        mock.when(
            HttpRequest.request()
                .withPath("/asdas"),
            Times.once()
        ).respond(
            HttpResponse.response()
                .withHeader("Content-Type", "application/json")
                .withBody(
                    IOUtils.resourceToString(
                        "/json/original.json",
                        StandardCharsets.UTF_8
                    ),
                    MediaType.JSON_UTF_8
                )
        );
    }

    private void successAsset(final MockServerClient mock) throws IOException {
        mock.when(
            HttpRequest.request()
                .withPath("/asdas/-/asdas-1.0.0.tgz"),
            Times.once()
        ).respond(
            HttpResponse.response()
                .withHeader("Content-Type", "application/octet-stream")
                .withBody(
                    BinaryBody.binary(
                        IOUtils.resourceToByteArray("/binaries/asdas-1.0.0.tgz")
                    )
                )
        );
    }

    /**
     * Inner subclass to instantiate Node container.
     * @since 0.1
     */
    private static class NodeContainer extends GenericContainer<NodeContainer> {
        NodeContainer() {
            super("node:latest");
        }
    }
}
