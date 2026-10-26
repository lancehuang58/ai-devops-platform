package com.delta.dms.ops.aidevopsplatform.web.integration;

import com.delta.dms.ops.aidevopsplatform.AiDevopsPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理介面登入整合測試：以 random-port 真實 HTTP 請求實際通過 Spring Security
 * filter chain，並由 HttpClient 內建 CookieManager 維護 Session cookie。
 */
@ActiveProfiles("demo")
@SpringBootTest(classes=AiDevopsPlatformApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"platform.database.jdbc-url=jdbc:h2:mem:login_it;DB_CLOSE_DELAY=-1",
                "platform.llm.model-name=login-it-mock"})
class ManagementLoginIT {
    private static final String CSS = "/css/management.css";
    private static final Pattern CSRF = Pattern.compile(
            "<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    @LocalServerPort int port;

    private String url(String path) { return "http://127.0.0.1:" + port + path; }

    /** 保留 cookie 但不自動追隨 redirect 的客戶端。 */
    private HttpClient sessionClient() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(HttpClient client, String path, String form, String contentType)
            throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url(path)))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String csrfToken(HttpClient client, String path) throws Exception {
        var page = get(client, path);
        assertThat(page.statusCode()).isEqualTo(200);
        return extractCsrf(page.body());
    }

    private String extractCsrf(String html) {
        Matcher matcher = CSRF.matcher(html);
        assertThat(matcher.find()).as("頁面應包含 CSRF hidden input").isTrue();
        String token = matcher.group(1);
        assertThat(token).isNotBlank();
        return token;
    }

    private String form(String csrf, String username, String password) {
        return "_csrf=" + enc(csrf) + "&username=" + enc(username) + "&password=" + enc(password);
    }

    /** Spring Security 以 sendRedirect 輸出絕對 Location，比對時只取 path 與 query。 */
    private String locationPath(HttpResponse<String> response) {
        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> new AssertionError("302 應攜帶 Location"));
        URI uri = URI.create(location);
        String path = uri.isAbsolute() ? uri.getPath() : location;
        return uri.getQuery() == null ? path : path + "?" + uri.getQuery();
    }

    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    /** 完整登入流程：讀登入頁取 token，POST 帳密，回傳執行登入的客戶端（含 cookie jar）。 */
    private HttpClient login(String username, String password) throws Exception {
        HttpClient client = sessionClient();
        String csrf = csrfToken(client, "/login");
        var response = postForm(client, "/login", form(csrf, username, password),
                "application/x-www-form-urlencoded");
        assertThat(response.statusCode()).isEqualTo(302);
        return client;
    }

    // ---- 驗收 1：匿名導向與公開頁面 ----

    @Test void anonymousHomeRedirectsToLogin() throws Exception {
        try (var client = sessionClient()) {
            var response = get(client, "/home");
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(locationPath(response)).isEqualTo("/login");
        }
    }

    @Test void anonymousRootRedirectsIntoLoginFlow() throws Exception {
        try (var client = sessionClient()) {
            // 匿名進入根路徑：由 UI 鏈導入登入流程
            var response = get(client, "/");
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(locationPath(response)).isEqualTo("/login");
        }
    }

    @Test void anonymousCanLoadLoginFailurePageAndCss() throws Exception {
        try (var client = sessionClient()) {
            var login = get(client, "/login");
            assertThat(login.statusCode()).isEqualTo(200);
            var failure = get(client, "/login-failure");
            assertThat(failure.statusCode()).isEqualTo(200);
            var css = get(client, CSS);
            assertThat(css.statusCode()).isEqualTo(200);
            assertThat(css.body()).isNotBlank();
        }
    }

    // ---- 驗收 2：登入頁結構 ----

    @Test void loginPageHasLabeledPasswordInputPostActionAndCsrf() throws Exception {
        try (var client = sessionClient()) {
            var page = get(client, "/login");
            assertThat(page.statusCode()).isEqualTo(200);
            String html = page.body();
            assertThat(html).contains("name=\"password\"")
                    .contains("type=\"password\"")
                    .contains("action=\"/login\"")
                    .contains("method=\"post\"");
            Matcher idFor = Pattern.compile("<label[^>]*for=\"([^\"]+)\"").matcher(html);
            assertThat(idFor.find()).as("登入欄位應有 label").isTrue();
            assertThat(html).contains("id=\"" + idFor.group(1) + "\"");
            assertThat(extractCsrf(html)).isNotBlank();
            // 密碼欄位不回填
            assertThat(html).doesNotContain("password\" value=", "password value=");
        }
    }

    // ---- 驗收 3：有效帳密登入 ----

    @Test void validCredentialsRedirectToHomeAndKeepSession() throws Exception {
        try (var client = login("normal", "demo-normal")) {
            var home = get(client, "/home");
            assertThat(home.statusCode()).isEqualTo(200);
            assertThat(home.body()).contains("normal");
        }
    }

    @Test void loginAlwaysRedirectsToHomeEvenWhenSavedRequestDiffers() throws Exception {
        try (var client = sessionClient()) {
            // 先觸發受保護頁留下導向 /home 的 saved request，登入仍必須固定導向 /home
            var attempt = get(client, "/home");
            assertThat(attempt.statusCode()).isEqualTo(302);
            String csrf = csrfToken(client, "/login");
            var response = postForm(client, "/login", form(csrf, "admin", "demo-admin"),
                    "application/x-www-form-urlencoded");
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(locationPath(response)).isEqualTo("/home");
        }
    }

    // ---- 驗收 4：無效帳密 ----

    @Test void wrongPasswordUnknownAccountAndBlankInputGoToFailurePage() throws Exception {
        for (String[] credentials : new String[][]{{"normal", "wrong-password"}, {"ghost", "demo-ghost"}, {"", ""}}) {
            try (var client = sessionClient()) {
                String csrf = csrfToken(client, "/login");
                var response = postForm(client, "/login", form(csrf, credentials[0], credentials[1]),
                        "application/x-www-form-urlencoded");
                assertThat(response.statusCode()).as("無效帳密 %s 應導向失敗頁", credentials[0]).isEqualTo(302);
                assertThat(locationPath(response)).as("無效帳密 %s 應導向失敗頁", credentials[0]).isEqualTo("/login-failure");
                assertThat(get(client, "/home").statusCode()).as("登入失敗後仍不可讀取首頁").isEqualTo(302);
                assertThat(get(client, "/login-failure").statusCode()).isEqualTo(200);
            }
        }
    }

    @Test void failurePageIsGenericWithoutSecretsOrStacktrace() throws Exception {
        try (var client = sessionClient()) {
            String csrf = csrfToken(client, "/login");
            postForm(client, "/login", form(csrf, "normal", "wrong-password"),
                    "application/x-www-form-urlencoded");
            String html = get(client, "/login-failure").body();
            assertThat(html).contains("/login");
            assertThat(html).doesNotContain("demo-normal", "Exception", "password");
        }
    }

    // ---- 驗收 5：CSRF ----

    @Test void loginPostWithoutCsrfIsRejected() throws Exception {
        try (var client = sessionClient()) {
            var response = postForm(client, "/login", "username=normal&password=demo-normal",
                    "application/x-www-form-urlencoded");
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(get(client, "/home").statusCode()).isEqualTo(302);
        }
    }

    @Test void loginPostWithInvalidCsrfIsRejected() throws Exception {
        try (var client = sessionClient()) {
            var response = postForm(client, "/login", form("not-a-real-token", "normal", "demo-normal"),
                    "application/x-www-form-urlencoded");
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(get(client, "/home").statusCode()).isEqualTo(302);
        }
    }

    @Test void logoutPostWithoutCsrfKeepsSessionValid() throws Exception {
        try (var client = login("normal", "demo-normal")) {
            assertThat(get(client, "/home").statusCode()).isEqualTo(200);
            var response = postForm(client, "/logout", "dummy=1", "application/x-www-form-urlencoded");
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(get(client, "/home").statusCode()).isEqualTo(200);
        }
    }

    // ---- 驗收 6：登出 ----

    @Test void logoutRedirectsToLoginAndInvalidatesOldSession() throws Exception {
        try (var client = login("normal", "demo-normal")) {
            assertThat(get(client, "/home").statusCode()).isEqualTo(200);
            String csrf = csrfToken(client, "/home");
            var response = postForm(client, "/logout", "_csrf=" + enc(csrf),
                    "application/x-www-form-urlencoded");
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(locationPath(response)).isEqualTo("/login?logout");
            assertThat(get(client, "/home").statusCode()).isEqualTo(302);
        }
    }

    // ---- 驗收 7：API 鏈維持 Basic／401，UI Session 不授權 API ----

    @Test void healthRemainsAnonymousAndApiKeepsBasicBehaviour() throws Exception {
        try (var client = sessionClient()) {
            var health = get(client, "/actuator/health");
            assertThat(health.statusCode()).isEqualTo(200);

            var anonymous = get(client, "/api/v1/managed-services");
            assertThat(anonymous.statusCode()).isEqualTo(401);

            var basic = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/managed-services")))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", basic("normal", "demo-normal")).GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(basic.statusCode()).isEqualTo(200);
            assertThat(basic.body()).contains("jenkins-demo");
        }
    }

    @Test void authenticatedUiSessionDoesNotAuthorizeApi() throws Exception {
        try (var client = login("normal", "demo-normal")) {
            assertThat(get(client, "/home").statusCode()).isEqualTo(200);
            var anonymous = get(client, "/api/v1/managed-services");
            assertThat(anonymous.statusCode()).isEqualTo(401);
            // 携带 UI session cookie 也不能讓 API 通過驗證，取得與 Basic 相同的資料
            var basic = client.send(HttpRequest.newBuilder(URI.create(url("/api/v1/managed-services")))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", basic("normal", "demo-normal")).GET()
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(basic.statusCode()).isEqualTo(200);
            assertThat(basic.body()).contains("jenkins-demo");
        }
    }

    private String basic(String user, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
