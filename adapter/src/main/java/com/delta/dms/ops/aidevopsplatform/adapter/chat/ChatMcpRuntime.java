package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.support.ToolCallbacks;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;

public final class ChatMcpRuntime implements AutoCloseable {
    private final McpSyncServer server;
    private final McpSyncClient client;
    private final List<McpSyncClient> remotes = new ArrayList<>();
    public ChatMcpRuntime(ReadOnlyCatalogTools tools) {
        var transport = new LocalMcpTransport();
        var specs = McpToolUtils.toSyncToolSpecifications(ToolCallbacks.from(tools)).stream().map(s -> {
            var t = s.tool();
            var annotated = McpSchema.Tool.builder(t.name()).description(t.description()).inputSchema(t.inputSchema())
                    .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).destructiveHint(false).build()).build();
            return new McpServerFeatures.SyncToolSpecification(annotated, s.callHandler());
        }).toList();
        server = McpServer.sync(transport).serverInfo("platform-readonly", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build()).tools(specs).build();
        client = McpClient.sync(transport.clientTransport()).requestTimeout(Duration.ofSeconds(10)).build();
        try { client.initialize(); } catch (RuntimeException ex) { close(); throw new IllegalStateException("local MCP initialization failed"); }
    }
    public McpSyncClient client() { return client; }
    public McpChatTools tools(Set<String> resources, Map<String, McpChatTools.Binding> overrides) {
        var bindings = new HashMap<String, McpChatTools.Binding>();
        for (String name : List.of("catalog", "status", "health")) bindings.put(name, new McpChatTools.Binding(client, name));
        bindings.putAll(overrides);
        return new McpChatTools(bindings, resources);
    }
    public Map<String, McpChatTools.Binding> connect(List<ChatSettings.Remote> servers) {
        var bindings = new HashMap<String, McpChatTools.Binding>();
        try {
            for (var remote : servers) {
                var uri = URI.create(remote.url());
                var request = HttpRequest.newBuilder();
                if (remote.token() != null) request.header("Authorization", "Bearer " + remote.token());
                var transport = HttpClientStreamableHttpTransport.builder(uri.getScheme() + "://" + uri.getRawAuthority())
                        .endpoint(uri.getPath().isEmpty() ? "/mcp" : uri.getPath()).requestBuilder(request)
                        .connectTimeout(Duration.ofSeconds(3)).maxResponseSize(65536).build();
                var remoteClient = McpClient.sync(transport).initializationTimeout(Duration.ofSeconds(10))
                        .requestTimeout(Duration.ofSeconds(10)).build();
                remotes.add(remoteClient);
                remoteClient.initialize();
                var tools = remoteClient.listTools().tools();
                for (var binding : remote.bindings().entrySet()) {
                    var tool = tools.stream().filter(t -> t.name().equals(binding.getValue())).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("configured MCP tool missing"));
                    if (tool.annotations() == null || !Boolean.TRUE.equals(tool.annotations().readOnlyHint())
                            || Boolean.TRUE.equals(tool.annotations().destructiveHint())) throw new IllegalArgumentException("MCP tool is not read-only");
                    if (bindings.putIfAbsent(binding.getKey(), new McpChatTools.Binding(remoteClient, binding.getValue())) != null)
                        throw new IllegalArgumentException("duplicate MCP route");
                }
            }
            return Map.copyOf(bindings);
        } catch (RuntimeException ex) { close(); throw new IllegalArgumentException("MCP configuration or connection rejected"); }
    }
    public void close() {
        remotes.forEach(McpSyncClient::close);
        client.close();
        server.close();
    }
}
