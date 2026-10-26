package com.delta.dms.ops.aidevopsplatform.web.integration;

import com.delta.dms.ops.aidevopsplatform.AiDevopsPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 預設 profile 不得載入 demo 管理頁面或可登入的 demo 身分。 */
@SpringBootTest(classes=AiDevopsPlatformApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class DefaultProfileIsolationIT {
    @LocalServerPort int port;

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test void healthEndpointStaysAnonymousInDefaultProfile() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test void managementPagesAreNotAvailableInDefaultProfile() throws Exception {
        for (String path : new String[]{"/login", "/login-failure", "/home"}) {
            assertThat(get(path).statusCode()).as("default profile 不應提供 %s", path).isNotEqualTo(200);
        }
    }
}
