package com.delta.dms.ops.aidevopsplatform.adapter.jira;

import com.delta.dms.ops.aidevopsplatform.service.port.out.JiraPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** HTTP adapter. A non-2xx response is a transient failure for the outbox. */
public final class JiraHttpGateway implements JiraPort {
    private final HttpClient http;
    private final URI issues;

    public JiraHttpGateway(String baseUrl) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), baseUrl);
    }

    JiraHttpGateway(HttpClient http, String baseUrl) {
        this.http = http;
        var root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.issues = URI.create(root + "/rest/api/2/issue");
    }

    @Override
    public boolean createIssue(String itemNo, String summary) {
        var body = "{\"fields\":{\"project\":{\"key\":\"OPS\"},\"summary\":\"" + escape(itemNo + " " + summary)
                + "\",\"issuetype\":{\"name\":\"Task\"}}}";
        try {
            var response = http.send(HttpRequest.newBuilder(issues)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            return status >= 200 && status < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("jira call interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("jira call failed", e);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
