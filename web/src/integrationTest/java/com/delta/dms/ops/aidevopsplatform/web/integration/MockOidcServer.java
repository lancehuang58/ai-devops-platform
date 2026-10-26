package com.delta.dms.ops.aidevopsplatform.web.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Local OpenID provider. Tests never call a company GitLab. */
final class MockOidcServer implements AutoCloseable {
    private final HttpServer server;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final Map<String, String> nonceByCode = new ConcurrentHashMap<>();

    MockOidcServer() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        publicKey = (RSAPublicKey) pair.getPublic();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String issuer() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    @Override public void close() { server.stop(0); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            switch (exchange.getRequestURI().getPath()) {
                case "/.well-known/openid-configuration" -> json(exchange, discovery());
                case "/oauth/authorize" -> authorize(exchange);
                case "/oauth/token" -> token(exchange);
                case "/oauth/jwks" -> json(exchange, jwks());
                case "/oauth/userinfo" -> json(exchange, userinfo());
                default -> {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                }
            }
        } catch (Exception ex) {
            var body = ex.getMessage() == null ? "error" : ex.getMessage();
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private String discovery() {
        var issuer = issuer();
        return """
                {"issuer":"%s","authorization_endpoint":"%s/oauth/authorize","token_endpoint":"%s/oauth/token","userinfo_endpoint":"%s/oauth/userinfo","jwks_uri":"%s/oauth/jwks","response_types_supported":["code"],"subject_types_supported":["public"],"id_token_signing_alg_values_supported":["RS256"],"scopes_supported":["openid","profile","email"],"token_endpoint_auth_methods_supported":["client_secret_basic","client_secret_post"],"claims_supported":["sub","name","email","groups"]}
                """.formatted(issuer, issuer, issuer, issuer, issuer);
    }

    private void authorize(HttpExchange exchange) throws IOException {
        var query = query(exchange.getRequestURI().getRawQuery());
        var code = UUID.randomUUID().toString();
        nonceByCode.put(code, query.getOrDefault("nonce", ""));
        var joiner = query.getOrDefault("redirect_uri", "").contains("?") ? "&" : "?";
        redirect(exchange, query.get("redirect_uri") + joiner + "code=" + code + "&state=" + url(query.get("state")));
    }

    private void token(HttpExchange exchange) throws Exception {
        var form = query(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        var code = form.get("code");
        var nonce = nonceByCode.remove(code);
        if (nonce == null) {
            json(exchange, "{\"error\":\"invalid_grant\"}");
            return;
        }
        var clientId = form.containsKey("client_id") ? form.get("client_id") : clientId(exchange);
        var access = UUID.randomUUID().toString();
        var now = Instant.now().getEpochSecond();
        var payload = """
                {"iss":"%s","sub":"ops","aud":"%s","exp":%d,"iat":%d,"nonce":"%s","email":"ops@example.internal","name":"Ops","groups":["admins"]}
                """.formatted(issuer(), clientId, now + 300, now, nonce).replace("\n", "");
        json(exchange, "{\"access_token\":\"" + access + "\",\"id_token\":\"" + jwt(payload) + "\",\"token_type\":\"Bearer\",\"expires_in\":300}");
    }

    private String jwks() {
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\""
                + b64(unsigned(publicKey.getModulus().toByteArray())) + "\",\"e\":\""
                + b64(unsigned(publicKey.getPublicExponent().toByteArray())) + "\"}]}";
    }

    private static String userinfo() {
        return "{\"sub\":\"ops\",\"email\":\"ops@example.internal\",\"name\":\"Ops\",\"groups\":[\"admins\"]}";
    }

    private String jwt(String payload) throws Exception {
        var signingInput = b64("{\"alg\":\"RS256\",\"kid\":\"test\",\"typ\":\"JWT\"}") + "." + b64(payload);
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + b64(signature.sign());
    }

    private static String clientId(HttpExchange exchange) {
        var header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) return "";
        var decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
        var split = decoded.indexOf(':');
        return split < 0 ? decoded : decoded.substring(0, split);
    }

    private static Map<String, String> query(String raw) {
        var values = new LinkedHashMap<String, String>();
        if (raw == null || raw.isBlank()) return values;
        for (var part : raw.split("&")) {
            var eq = part.indexOf('=');
            if (eq < 0) continue;
            values.put(urlDecode(part.substring(0, eq)), urlDecode(part.substring(eq + 1)));
        }
        return values;
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static byte[] unsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            var stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }

    private static String b64(String text) { return b64(text.getBytes(StandardCharsets.UTF_8)); }
    private static String b64(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String url(String value) { return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private static String urlDecode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
}
