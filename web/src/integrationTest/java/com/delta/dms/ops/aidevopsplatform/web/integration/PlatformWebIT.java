package com.delta.dms.ops.aidevopsplatform.web.integration;

import com.delta.dms.ops.aidevopsplatform.AiDevopsPlatformApplication;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ClosureReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ItemType;
import com.delta.dms.ops.aidevopsplatform.model.workitem.SourceKind;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItem;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemStore;
import com.delta.dms.ops.aidevopsplatform.service.workflow.TrackedWorkItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("platform")
@SpringBootTest(classes = AiDevopsPlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "platform.workflow.workers=0")
class PlatformWebIT {
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static final MockOidcServer OIDC = startOidc();
    private static final Pattern CSRF = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    static { POSTGRES.start(); }
    private static final Instant NOW = Instant.parse("2026-10-26T09:00:00Z");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("platform.auth.alert-token", () -> "test-token");
        registry.add("platform.gitlab.base-url", OIDC::issuer);
        registry.add("GITLAB_BASE_URL", OIDC::issuer);
        registry.add("GITLAB_OAUTH_CLIENT_ID", () -> "platform-test");
        registry.add("GITLAB_OAUTH_CLIENT_SECRET", () -> "platform-test-secret");
        for (var name : new String[]{"GITLAB_TOKEN", "JENKINS_TOKEN", "GLOVE_TOKEN", "JIRA_TOKEN", "TEAMS_WEBHOOK", "LITELLM_API_KEY"}) {
            registry.add(name, () -> "test-secret");
        }
    }

    private static MockOidcServer startOidc() {
        try { return new MockOidcServer(); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    @LocalServerPort int port;
    @Autowired WorkItemStore items;

    @Test void anonymousWorkItemsRedirectToLoginAndHtmxIsLocal() throws Exception {
        try (var client = client()) {
            var page = get(client, "/work-items");
            assertThat(page.statusCode()).isEqualTo(302);
            assertThat(location(page)).startsWith("/login");
            var login = get(client, "/login");
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(login.body()).contains("GitLab", "/oauth2/authorization/gitlab").doesNotContain("name=\"password\"");
            var script = get(client, "/vendor/htmx/htmx.min.js");
            assertThat(script.statusCode()).isEqualTo(200);
            assertThat(script.body()).contains("htmx");
        }
    }

    @Test void loginListsFiltersAndShowsTheTimeline() throws Exception {
        postAlert("evt-web-1");
        var duplicate = postAlert("evt-web-1");
        assertThat(duplicate.statusCode()).isEqualTo(202);
        assertThat(duplicate.body()).contains("\"duplicates\":1");
        try (var client = login("/work-items?system=jenkins")) {
            var list = get(client, "/work-items?system=jenkins");
            assertThat(list.statusCode()).isEqualTo(200);
            assertThat(list.body()).contains("log disk high", "/vendor/htmx/htmx.min.js", "value=\"50\"");
            assertThat(get(client, "/work-items?status=CLOSED").body()).doesNotContain("log disk high");
            assertThat(get(client, "/work-items?system=jenkins").body()).contains("log disk high");
            assertThat(get(client, "/work-items?from=2099-01-01T00:00&to=2099-01-02T00:00").body()).doesNotContain("log disk high");
            var detail = get(client, "/work-items/" + itemNo(list.body(), "log disk high", client));
            assertThat(detail.body()).contains("WORK_ITEM_OPENED", "時間軸");
        }
    }

    @Test void closureConfirmationAndMaskedTakeoverAreManual() throws Exception {
        var review = WorkItem.open(UUID.randomUUID(), "OPS-0000000401", ItemType.INCIDENT, "needs confirmation", "ops",
                SourceKind.USER, "PROD", NOW, UUID.randomUUID());
        review.beginCollecting(NOW, UUID.randomUUID());
        review.beginMatching(NOW, UUID.randomUUID());
        review.noMatch(NOW, UUID.randomUUID());
        review.withdraw(ClosureReason.RESOLVED, NOW, UUID.randomUUID());
        items.save(TrackedWorkItem.fresh(review));
        var handoff = WorkItem.open(UUID.randomUUID(), "OPS-0000000402", ItemType.INCIDENT, "needs a person", "ops",
                SourceKind.USER, "PROD", NOW, UUID.randomUUID());
        items.save(TrackedWorkItem.fresh(handoff));
        try (var client = login()) {
            var reviewPage = get(client, "/work-items/OPS-0000000401");
            var closed = postForm(client, "/work-items/OPS-0000000401/closure",
                    "_csrf=" + enc(csrf(reviewPage.body())) + "&reason=RESOLVED&duplicateOf=");
            assertThat(closed.statusCode()).isEqualTo(302);
            assertThat(get(client, "/work-items/OPS-0000000401").body()).contains("CLOSED", "RESOLVED");

            var page = get(client, "/work-items/OPS-0000000402");
            postForm(client, "/work-items/OPS-0000000402/takeover",
                    "_csrf=" + enc(csrf(page.body())) + "&tool=CLAUDE_CODE&modelName=claude&reason=qwen+stalled");
            var zip = client.send(HttpRequest.newBuilder(URI.create(url("/work-items/OPS-0000000402/package?evidence=" + enc("password=s3cret on 10.1.2.3"))))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(zip.statusCode()).isEqualTo(200);
            var unpacked = unzip(zip.body());
            assertThat(unpacked).contains("README.md", "item.json", "evidence.txt", "[REDACTED:PASSWORD]").doesNotContain("s3cret");
            var after = get(client, "/work-items/OPS-0000000402");
            postForm(client, "/work-items/OPS-0000000402/write-back", "_csrf=" + enc(csrf(after.body()))
                    + "&summary=cleared&rootCause=logs&actions=rotated&evidence=df");
            var ready = get(client, "/work-items/OPS-0000000402");
            var forced = postForm(client, "/work-items/OPS-0000000402/force-close",
                    "_csrf=" + enc(csrf(ready.body())) + "&firingAcknowledged=true");
            assertThat(forced.statusCode()).isEqualTo(302);
            assertThat(get(client, "/work-items/OPS-0000000402").body()).contains("CLOSED", "FORCED_BY_COMMERCIAL_MODEL");
        }
    }

    @Test void webhookRejectsAMissingToken() throws Exception {
        try (var client = client()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/alerts/alertmanager")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(alertJson("evt-denied")))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
        }
    }

    @Test void chatIsBehindOidcAndCsrfAndCanClearSessionHistory() throws Exception {
        try (var anonymous = client()) {
            assertThat(get(anonymous, "/chat").statusCode()).isEqualTo(302);
        }
        try (var client = login("/chat")) {
            var page = get(client, "/chat");
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.body()).contains("AI 對話", "/vendor/htmx/htmx.min.js");
            assertThat(postForm(client, "/chat", "message=hello").statusCode()).isEqualTo(403);
            var sent = postForm(client, "/chat", "_csrf=" + enc(csrf(page.body())) + "&message=hello");
            assertThat(sent.statusCode()).isEqualTo(200);
            assertThat(sent.body()).contains("hello", "尚未啟用");
            assertThat(postForm(client, "/chat/clear", "_csrf=" + enc(csrf(sent.body()))).statusCode()).isEqualTo(302);
            assertThat(get(client, "/chat").body()).doesNotContain("hello");
        }
    }

    private HttpResponse<String> postAlert(String eventSuffix) throws Exception {
        try (var client = client()) {
            return client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/alerts/alertmanager")))
                    .header("Content-Type", "application/json")
                    .header("X-Alert-Token", "test-token")
                    .header("X-Alert-Source", "am-it")
                    .POST(HttpRequest.BodyPublishers.ofString(alertJson(eventSuffix)))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static String alertJson(String fingerprint) {
        return """
                {"version":"4","alerts":[{"status":"firing","labels":{"alertname":"DiskUsageHigh","severity":"warning","instance":"vm-web-01","env":"prod","service":"jenkins","mountpoint":"/var/log"},"annotations":{"summary":"log disk high"},"startsAt":"2026-10-26T08:00:00Z","endsAt":"0001-01-01T00:00:00Z","fingerprint":"%s"}]}
                """.formatted(fingerprint);
    }

    private String itemNo(String html, String title, HttpClient client) throws Exception {
        var matcher = Pattern.compile("href=\"/work-items/(OPS-\\d+)\"").matcher(html);
        while (matcher.find()) {
            var no = matcher.group(1);
            if (get(client, "/work-items/" + no).body().contains(title)) return no;
        }
        throw new AssertionError("missing " + title);
    }

    private HttpClient login() throws Exception { return login("/work-items"); }

    private HttpClient login(String deepLink) throws Exception {
        var client = client();
        var anonymous = get(client, deepLink);
        assertThat(anonymous.statusCode()).isEqualTo(302);
        assertThat(location(anonymous)).startsWith("/login");
        var form = get(client, "/login");
        assertThat(form.body()).contains("GitLab", "/oauth2/authorization/gitlab").doesNotContain("name=\"password\"");
        var started = get(client, "/oauth2/authorization/gitlab");
        assertThat(started.statusCode()).isEqualTo(302);
        var authorize = started.headers().firstValue("Location").orElseThrow();
        var decoded = java.net.URLDecoder.decode(authorize, StandardCharsets.UTF_8);
        assertThat(decoded).contains("openid", "profile", "email", "client_id=platform-test");
        var granted = client.send(HttpRequest.newBuilder(URI.create(authorize)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(granted.statusCode()).isEqualTo(302);
        var landed = follow(client, granted.headers().firstValue("Location").orElseThrow());
        assertThat(withoutContinue(landed)).isEqualTo(deepLink);
        return client;
    }

    private String follow(HttpClient client, String start) throws Exception {
        var current = start;
        for (int hop = 0; hop < 8; hop++) {
            var request = current.startsWith("http")
                    ? HttpRequest.newBuilder(URI.create(current))
                    : HttpRequest.newBuilder(URI.create(url(current)));
            var response = client.send(request.timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 302) return pathAndQuery(current);
            current = response.headers().firstValue("Location").orElseThrow();
        }
        throw new AssertionError("too many redirects from " + start);
    }

    private HttpClient client() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path))).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(HttpClient client, String path, String form) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path))).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String csrf(String html) {
        Matcher matcher = CSRF.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String withoutContinue(String path) {
        return path.replace("&continue", "").replace("?continue", "");
    }

    private static String pathAndQuery(String current) {
        var uri = URI.create(current);
        if (!uri.isAbsolute()) return current;
        return uri.getRawQuery() == null ? uri.getRawPath() : uri.getRawPath() + "?" + uri.getRawQuery();
    }

    private String location(HttpResponse<String> response) {
        var location = response.headers().firstValue("Location").orElseThrow();
        var uri = URI.create(location);
        var path = uri.isAbsolute() ? uri.getPath() : location;
        return uri.getQuery() == null ? path : path + "?" + uri.getQuery();
    }

    private String url(String path) { return "http://127.0.0.1:" + port + path; }

    private static String unzip(byte[] body) throws Exception {
        var text = new StringBuilder();
        try (var archive = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(body))) {
            java.util.zip.ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                text.append(entry.getName()).append('\n').append(new String(archive.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return text.toString();
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
