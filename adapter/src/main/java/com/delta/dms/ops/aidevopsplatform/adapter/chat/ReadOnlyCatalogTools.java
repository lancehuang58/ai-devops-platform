package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import com.delta.dms.ops.aidevopsplatform.model.catalog.ServiceCatalog;
import com.delta.dms.ops.aidevopsplatform.model.catalog.MonitoredService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.function.Function;

public final class ReadOnlyCatalogTools {
    private final ServiceCatalog catalog;
    private final Function<String, String> status;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public ReadOnlyCatalogTools(ServiceCatalog catalog, Function<String, String> status) { this.catalog = catalog; this.status = status; }
    private MonitoredService find(String resource) {
        return catalog.monitored().stream().filter(s -> s.id().equals(resource)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown catalog resource"));
    }
    @Tool(description = "唯讀查詢已登記服務的安全設定（不含 credential 或 URL）")
    public String catalog(@ToolParam(description = "服務目錄資源 id") String resource) {
        var s = find(resource);
        return "id=" + s.id() + ";type=" + s.type() + ";environment=" + s.environment();
    }
    @Tool(description = "唯讀查詢監控資料庫的最近探測結果，非即時健康保證")
    public String status(@ToolParam(description = "服務目錄資源 id") String resource) { find(resource); return status.apply(resource); }
    @Tool(description = "只對服務目錄預先登記的健康端點執行 HTTP GET，不跟隨重新導向")
    public String health(@ToolParam(description = "服務目錄資源 id，不能輸入 URL") String resource) {
        var s = find(resource);
        try {
            var uri = URI.create(s.endpointUrl());
            if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                return "健康端點設定不安全，未查詢";
            var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return "HTTP " + response.statusCode() + "（來源：服務目錄健康端點；未讀取內容；3xx 未跟隨）";
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return "探測中斷，健康未知"; }
        catch (Exception ex) { return "探測失敗，健康未知"; }
    }
}
