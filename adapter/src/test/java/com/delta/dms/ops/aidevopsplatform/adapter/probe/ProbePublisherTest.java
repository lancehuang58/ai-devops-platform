package com.delta.dms.ops.aidevopsplatform.adapter.probe;

import com.delta.dms.ops.aidevopsplatform.adapter.alert.AlertIntake;
import com.delta.dms.ops.aidevopsplatform.adapter.persistence.JdbcPlatformStore;
import com.delta.dms.ops.aidevopsplatform.model.alert.Fingerprint;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelVerdict;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbePublication;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;
import com.delta.dms.ops.aidevopsplatform.service.monitoring.ProbeRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProbePublisherTest {
    static PostgreSQLContainer postgres;
    static JdbcPlatformStore store;
    static ProbePublisher publisher;
    static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

    @BeforeAll
    static void database() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        store = JdbcPlatformStore.migrateAndOpen(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        publisher = new ProbePublisher(store, new AlertIntake(store));
    }

    @Test void unhealthyAnswerOpensOneTicketAndALaterProbeDoesNot() {
        var target = target("jenkins-unhealthy", ProbeChannel.ENDPOINT);
        publish(target, NOW, endpoint(ChannelVerdict.UNHEALTHY, "HTTP 503"));
        publish(target, NOW.plusSeconds(60), endpoint(ChannelVerdict.UNHEALTHY, "HTTP 503"));
        assertThat(store.countOpenWorkItemsForResource("jenkins-unhealthy")).isEqualTo(1);
        assertThat(store.openWorkItemTitle("jenkins-unhealthy")).contains("HTTP 503").contains("ENDPOINT");
        assertThat(store.occurrenceCount(Fingerprint.of("prod", "jenkins-unhealthy", "SERVICE_UNAVAILABLE", Map.of()))).isEqualTo(2);
        assertThat(store.probeResultCount("jenkins-unhealthy")).isEqualTo(2);
        assertThat(store.activityDetail("jenkins-unhealthy")).contains("ENDPOINT", "HTTP 503");
        assertThat(store.sourceType("probe")).isEqualTo("HTTP_ENDPOINT_PROBE");
        assertThat(store.notificationCount("channel-degraded:jenkins-unhealthy:ENDPOINT")).isZero();
    }

    @Test void higherChannelFailureWithAHealthyLowerChannelNotifiesOnceAndOpensNoTicket() {
        var target = target("jenkins-degraded", ProbeChannel.ENDPOINT, ProbeChannel.API);
        publish(target, NOW, endpoint(ChannelVerdict.CHANNEL_FAILED, "timeout"), api(ChannelVerdict.HEALTHY, "200"));
        publish(target, NOW.plusSeconds(30), endpoint(ChannelVerdict.CHANNEL_FAILED, "connection refused"), api(ChannelVerdict.HEALTHY, "200"));
        assertThat(store.countOpenWorkItemsForResource("jenkins-degraded")).isZero();
        assertThat(store.notificationCount("channel-degraded:jenkins-degraded:ENDPOINT")).isEqualTo(1);
        assertThat(store.probeResultCount("jenkins-degraded")).isEqualTo(4);
    }

    @Test void allChannelFailuresOpenOneIncidentCarryingEveryReason() {
        var target = target("jenkins-down", ProbeChannel.ENDPOINT, ProbeChannel.MCP, ProbeChannel.VM);
        publish(target, NOW,
                endpoint(ChannelVerdict.CHANNEL_FAILED, "connection refused"),
                mcp(ChannelVerdict.CHANNEL_FAILED, "mcp server unavailable"),
                unusedApi(),
                glove(ChannelVerdict.CHANNEL_FAILED, "auth failure"));
        assertThat(store.countOpenWorkItemsForResource("jenkins-down")).isEqualTo(1);
        assertThat(store.openWorkItemTitle("jenkins-down"))
                .contains("connection refused", "mcp server unavailable", "auth failure");
        assertThat(store.activityDetail("jenkins-down")).contains("connection refused", "mcp server unavailable", "auth failure");
        assertThat(store.occurrenceCount(Fingerprint.of("prod", "jenkins-down", "HEALTH_CHECK_FAILED", Map.of()))).isEqualTo(1);
    }

    private static void publish(ProbeTarget target, Instant when, EndpointProbe endpoint, McpProbe mcp, ApiProbe api, GloveProbe glove) {
        var decision = new ProbeRunner().check(target, endpoint, mcp, api, glove);
        publisher.publish(new ProbePublication(target, decision, when));
    }

    private static void publish(ProbeTarget target, Instant when, EndpointProbe endpoint) {
        publish(target, when, endpoint, unusedMcp(), unusedApi(), unusedGlove());
    }

    private static void publish(ProbeTarget target, Instant when, EndpointProbe endpoint, ApiProbe api) {
        publish(target, when, endpoint, unusedMcp(), api, unusedGlove());
    }

    private static ProbeTarget target(String instanceId, ProbeChannel... channels) {
        return new ProbeTarget(instanceId, "jenkins", "prod", java.util.List.of(channels),
                "https://jenkins.example.internal/login", "https://jenkins.example.internal/api", "glove", "vm-jenkins-01");
    }

    private interface EndpointProbe extends com.delta.dms.ops.aidevopsplatform.service.port.out.EndpointProbePort {}
    private interface McpProbe extends com.delta.dms.ops.aidevopsplatform.service.port.out.McpProbePort {}
    private interface ApiProbe extends com.delta.dms.ops.aidevopsplatform.service.port.out.ApiProbePort {}
    private interface GloveProbe extends com.delta.dms.ops.aidevopsplatform.service.port.out.GloveProbePort {}

    private static EndpointProbe endpoint(ChannelVerdict verdict, String detail) {
        return target -> new ChannelAttempt(ProbeChannel.ENDPOINT, verdict, detail);
    }
    private static McpProbe mcp(ChannelVerdict verdict, String detail) {
        return target -> new ChannelAttempt(ProbeChannel.MCP, verdict, detail);
    }
    private static ApiProbe api(ChannelVerdict verdict, String detail) {
        return target -> new ChannelAttempt(ProbeChannel.API, verdict, detail);
    }
    private static GloveProbe glove(ChannelVerdict verdict, String detail) {
        return target -> new ChannelAttempt(ProbeChannel.VM, verdict, detail);
    }
    private static McpProbe unusedMcp() { return target -> { throw new AssertionError("mcp"); }; }
    private static ApiProbe unusedApi() { return target -> { throw new AssertionError("api"); }; }
    private static GloveProbe unusedGlove() { return target -> { throw new AssertionError("glove"); }; }
}
