package com.delta.dms.ops.aidevopsplatform.adapter.jira;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JiraHttpGatewayTest {
    @Test void transientFailureThenCreatedIssue() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/api/2/issue", exchange -> {
            int n = calls.incrementAndGet();
            byte[] body = (n == 1 ? "" : "{\"key\":\"OPS-1\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(n == 1 ? 503 : 201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var gateway = new JiraHttpGateway("http://127.0.0.1:" + server.getAddress().getPort());
            assertThat(gateway.createIssue("OPS-1", "disk")).isFalse();
            assertThat(gateway.createIssue("OPS-1", "disk")).isTrue();
            assertThat(calls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }
}
