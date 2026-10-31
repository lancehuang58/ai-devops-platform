package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import io.modelcontextprotocol.spec.*;
import io.modelcontextprotocol.json.*;
import reactor.core.publisher.Mono;
import java.util.function.Function;

/** Private in-process JSON-RPC connection; no network endpoint or unauthenticated tool exposure. */
public final class LocalMcpTransport implements McpServerTransportProvider {
    private final McpJsonMapper mapper = McpJsonDefaults.getMapper();
    private McpServerSession session;
    private Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> receiver;
    private final McpServerTransport serverTransport = new McpServerTransport() {
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return Mono.defer(() -> receiver.apply(Mono.just(message)).then());
        }
        public <T> T unmarshalFrom(Object value, TypeRef<T> type) { return mapper.convertValue(value, type); }
        public Mono<Void> closeGracefully() { return Mono.empty(); }
    };
    private final McpClientTransport clientTransport = new McpClientTransport() {
        public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
            receiver = handler; return Mono.empty();
        }
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) { return Mono.defer(() -> session.handle(message)); }
        public <T> T unmarshalFrom(Object value, TypeRef<T> type) { return mapper.convertValue(value, type); }
        public Mono<Void> closeGracefully() { return Mono.empty(); }
    };
    public McpClientTransport clientTransport() { return clientTransport; }
    public void setSessionFactory(McpServerSession.Factory factory) { session = factory.create(serverTransport); }
    public Mono<Void> notifyClients(String method, Object params) { return session.sendNotification(method, params); }
    public Mono<Void> closeGracefully() { return session == null ? Mono.empty() : session.closeGracefully(); }
}
