package com.delta.dms.ops.aidevopsplatform.web.platform;

import com.delta.dms.ops.aidevopsplatform.adapter.alert.AlertIntake;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcPlatformStore;
import com.delta.dms.ops.aidevopsplatform.model.alert.AlertmanagerAlert;
import com.delta.dms.ops.aidevopsplatform.model.alert.GenericAlert;
import com.delta.dms.ops.aidevopsplatform.model.alert.InvalidAlertException;
import com.delta.dms.ops.aidevopsplatform.service.event.NotificationFanout;
import com.delta.dms.ops.aidevopsplatform.service.port.out.TeamsNotificationPort;
import com.delta.dms.ops.aidevopsplatform.web.config.PlatformAuthProperties;
import tools.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Profile("platform")
public class AlertWebhookController {
    private final AlertIntake intake;
    private final JdbcPlatformStore store;
    private final NotificationFanout fanout;
    private final TeamsNotificationPort teams;
    private final PlatformAuthProperties auth;
    private final Clock clock;

    public AlertWebhookController(AlertIntake intake, JdbcPlatformStore store, NotificationFanout fanout,
                                  TeamsNotificationPort teams, PlatformAuthProperties auth, Clock clock) {
        this.intake = intake;
        this.store = store;
        this.fanout = fanout;
        this.teams = teams;
        this.auth = auth;
        this.clock = clock;
    }

    @PostMapping("/api/v1/alerts/alertmanager")
    public ResponseEntity<Map<String, Integer>> alertmanager(
            @RequestHeader(value = "X-Alert-Token", required = false) String token,
            @RequestHeader(value = "X-Alert-Source", defaultValue = "am-prod-01") String sourceId,
            @RequestBody JsonNode body) {
        if (!authorized(token)) return ResponseEntity.status(401).build();
        var version = text(body, "version");
        var alerts = body.get("alerts");
        if (alerts == null || !alerts.isArray() || alerts.isEmpty()) throw new InvalidAlertException("alerts required");
        int accepted = 0;
        int duplicates = 0;
        int opened = 0;
        var now = Instant.now(clock);
        for (var alert : alerts) {
            var result = intake.alertmanager(sourceId, version, new AlertmanagerAlert(required(alert, "status"),
                    map(alert.get("labels")), map(alert.get("annotations")), instant(alert, "startsAt"),
                    optionalInstant(alert, "endsAt"), text(alert, "generatorURL"), required(alert, "fingerprint")), now);
            accepted++;
            if (result.duplicate()) duplicates++;
            if (result.openedCase()) opened++;
        }
        deliver(now);
        return ResponseEntity.accepted().body(Map.of("accepted", accepted, "duplicates", duplicates, "opened", opened));
    }

    @PostMapping("/api/v1/alerts/generic")
    public ResponseEntity<Map<String, Integer>> generic(@RequestHeader(value = "X-Alert-Token", required = false) String token,
                                                         @RequestBody JsonNode body) {
        if (!authorized(token)) return ResponseEntity.status(401).build();
        var now = Instant.now(clock);
        var result = intake.generic(new GenericAlert(required(body, "sourceId"), required(body, "checkId"),
                required(body, "resourceId"), textOr(body, "resourceType", "VM"), required(body, "environment"),
                required(body, "symptomType"), required(body, "symptomCode"), map(body.get("discriminator")),
                text(body, "severity"), required(body, "status"), instant(body, "observedAt"), text(body, "summary")), now);
        deliver(now);
        return ResponseEntity.accepted().body(Map.of("accepted", 1, "duplicates", result.duplicate() ? 1 : 0, "opened", result.openedCase() ? 1 : 0));
    }

    @ExceptionHandler(InvalidAlertException.class)
    ResponseEntity<Map<String, String>> invalid(InvalidAlertException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid alert"));
    }

    private void deliver(Instant now) {
        fanout.deliver(store.pendingNotifications(), teams, key -> store.markNotificationSent(key, now));
    }

    private boolean authorized(String token) {
        var expected = auth.getAlertToken() == null ? "" : auth.getAlertToken();
        var presented = token == null ? "" : token;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))
                && !expected.isBlank();
    }

    private static String required(JsonNode node, String field) {
        var value = text(node, field);
        if (value == null || value.isBlank()) throw new InvalidAlertException(field + " required");
        return value;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        var value = text(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Instant instant(JsonNode node, String field) {
        var value = optionalInstant(node, field);
        if (value == null) throw new InvalidAlertException(field + " required");
        return value;
    }

    private static Instant optionalInstant(JsonNode node, String field) {
        var value = text(node, field);
        if (value == null || value.isBlank()) return null;
        return Instant.parse(value);
    }

    private static Map<String, String> map(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) return Map.of();
        var out = new LinkedHashMap<String, String>();
        node.properties().forEach(entry -> out.put(entry.getKey(), entry.getValue().asText()));
        return out;
    }
}
