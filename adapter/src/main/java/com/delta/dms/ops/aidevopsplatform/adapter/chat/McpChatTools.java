package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ChatTools;
import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Routes are trusted deployment configuration; neither user nor model supplies a server or tool name. */
public final class McpChatTools implements ChatTools {
    public record Binding(McpSyncClient client, String tool) {}
    private final Map<String, Binding> bindings;
    private final Set<String> resources;
    public McpChatTools(Map<String, Binding> bindings, Set<String> resources) {
        if (!Set.of("catalog", "status", "health").containsAll(bindings.keySet())) throw new IllegalArgumentException("unapproved tool");
        this.bindings = Map.copyOf(bindings); this.resources = Set.copyOf(resources);
    }
    public String read(String tool, String resource) {
        if (!resources.contains(resource)) throw new IllegalArgumentException("unknown resource");
        var binding = bindings.get(tool);
        if (binding == null) throw new IllegalArgumentException("unapproved tool");
        var result = binding.client().callTool(new McpSchema.CallToolRequest(binding.tool(), Map.of("resource", resource)));
        if (Boolean.TRUE.equals(result.isError())) return "MCP 查詢失敗，健康未知";
        return result.content().stream().filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast).map(McpSchema.TextContent::text).collect(Collectors.joining("\n"));
    }
}
