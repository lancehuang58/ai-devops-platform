package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import com.delta.dms.ops.aidevopsplatform.model.catalog.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ReadOnlyCatalogToolsTest {
    private ReadOnlyCatalogTools tools(String url) {
        var service = new MonitoredService("known", "jenkins", "prod", url, null, null, null,
                "${JENKINS_TOKEN}", List.of(), null, Map.of());
        return new ReadOnlyCatalogTools(new ServiceCatalog(List.of(), List.of(service), List.of()), id -> "尚無探測");
    }
    @Test void getOnlyAndNeverFollowRedirectsOrReturnResponseBody() throws Exception {
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            count.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "/private");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/private", exchange -> { count.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        try {
            var tools = tools("http://127.0.0.1:" + server.getAddress().getPort() + "/health");
            assertThat(tools.health("known")).contains("HTTP 302");
            assertThat(count.get()).isEqualTo(1);
            assertThatThrownBy(() -> tools.health("http://127.0.0.1/private")).isInstanceOf(IllegalArgumentException.class);
            assertThat(count.get()).isEqualTo(1);
            assertThat(tools.catalog("known")).doesNotContain("TOKEN", "http", "credential");
        } finally { server.stop(0); }
    }
    @Test void credentialsQueryAndNonHttpSchemesAreRejected() {
        for (var url : List.of("http://user:pass@localhost/", "http://localhost/?token=x", "file:///etc/passwd"))
            assertThat(tools(url).health("known")).contains("未查詢");
    }
    @Test void modelFactoryBuildsWithoutConnectingAndRejectsEmptySkill() {
        var settings = ChatSettings.load("api-key: ${QWEN_API_KEY}", name -> "test-only-value");
        assertThat(QwenChatFactory.create(settings, "需求分析", List.of("known"))).isNotNull();
        assertThatThrownBy(() -> QwenChatFactory.create(settings, "", List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
