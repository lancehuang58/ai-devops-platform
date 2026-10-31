package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class RemoteMcpTest {
    @Test void configuredRemoteReadonlyToolCanReplaceStatusWithoutCodeChanges() throws Exception {
        var calls = new AtomicInteger();
        var mapper = McpJsonDefaults.getMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405, -1); exchange.close(); return; }
            Map<?, ?> request = mapper.readValue(exchange.getRequestBody().readAllBytes(), Map.class);
            if (!request.containsKey("id")) { exchange.sendResponseHeaders(202, -1); exchange.close(); return; }
            Object result = switch ((String) request.get("method")) {
                case "initialize" -> Map.of("protocolVersion", "2025-11-25", "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "glove-stub", "version", "1"));
                case "tools/list" -> Map.of("tools", List.of(Map.of("name", "vm_status", "description", "readonly",
                        "inputSchema", Map.of("type", "object", "properties", Map.of("resource", Map.of("type", "string"))),
                        "annotations", Map.of("readOnlyHint", true, "destructiveHint", false))));
                case "tools/call" -> {
                    assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-only-token");
                    assertThat(request.get("params").toString()).contains("vm_status", "jenkins-prod").doesNotContain("command");
                    calls.incrementAndGet();
                    yield Map.of("content", List.of(Map.of("type", "text", "text", "VM running")), "isError", false);
                }
                default -> Map.of();
            };
            byte[] response = mapper.writeValueAsBytes(Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try (var runtime = new ChatMcpRuntime(new ReadOnlyCatalogTools(ChatMcpFlowTest.catalog(), id -> "local"))) {
            var remote = new ChatSettings.Remote("glove", "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
                    "test-only-token", Map.of("status", "vm_status"));
            var bindings = runtime.connect(List.of(remote));
            assertThat(runtime.tools(Set.of("jenkins-prod"), bindings).read("status", "jenkins-prod")).isEqualTo("VM running");
            assertThat(calls.get()).isEqualTo(1);
            assertThatThrownBy(() -> runtime.tools(Set.of("jenkins-prod"), bindings).read("status", "unknown"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }
    @Test void remoteConfigurationFailsClosedOnUnsafeOrAmbiguousBindings() {
        for (String server : List.of(
                "{id: glove, url: 'https://glove.invalid/mcp', read-only: false, bindings: {status: vm_status}}",
                "{id: glove, url: 'https://glove.invalid/mcp', read-only: true, bindings: {shell: execute}}",
                "{id: glove, url: 'https://user:pass@glove.invalid/mcp', read-only: true, bindings: {status: vm_status}}")) {
            assertThatThrownBy(() -> ChatSettings.load("api-key: ${QWEN_API_KEY}\nservers: [" + server + "]", n -> "test-only-key"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
