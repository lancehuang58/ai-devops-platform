package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

import com.delta.dms.ops.aidevopsplatform.adapter.execution.RejectingExecution;
import com.delta.dms.ops.aidevopsplatform.adapter.jira.JiraHttpGateway;
import com.delta.dms.ops.aidevopsplatform.adapter.jira.JiraOutboxDispatcher;
import com.delta.dms.ops.aidevopsplatform.adapter.llm.UnavailableLlm;
import com.delta.dms.ops.aidevopsplatform.adapter.workflow.JdbcResourceLock;
import com.delta.dms.ops.aidevopsplatform.adapter.workflow.WorkQueueWorker;
import com.delta.dms.ops.aidevopsplatform.model.alert.CanonicalAlert;
import com.delta.dms.ops.aidevopsplatform.model.alert.Fingerprint;
import com.delta.dms.ops.aidevopsplatform.model.alert.Severity;
import com.delta.dms.ops.aidevopsplatform.model.alert.SignalStatus;
import com.delta.dms.ops.aidevopsplatform.model.alert.SourceType;
import com.delta.dms.ops.aidevopsplatform.model.workitem.BlockedReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ClosureReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ItemType;
import com.delta.dms.ops.aidevopsplatform.model.workitem.SourceKind;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItem;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemStatus;
import com.delta.dms.ops.aidevopsplatform.service.workflow.ClosureService;
import com.delta.dms.ops.aidevopsplatform.service.workflow.FixCatalog;
import com.delta.dms.ops.aidevopsplatform.service.workflow.TrackedWorkItem;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemCoordinator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformFlowPersistenceTest {
    static PostgreSQLContainer postgres;
    static JdbcPlatformStore store;
    static JdbcWorkItemStore items;
    static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

    @BeforeAll
    static void database() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        store = JdbcPlatformStore.migrateAndOpen(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        items = new JdbcWorkItemStore(storeDataSource());
    }

    @Test void savedClosureReviewConfirmsOnce() {
        var opened = WorkItem.open(UUID.randomUUID(), "OPS-0000000301", ItemType.INCIDENT, "confirm me", "ops",
                SourceKind.USER, "PROD", NOW, UUID.randomUUID());
        opened.beginCollecting(NOW, UUID.randomUUID());
        opened.beginMatching(NOW, UUID.randomUUID());
        opened.noMatch(NOW, UUID.randomUUID());
        opened.withdraw(ClosureReason.RESOLVED, NOW, UUID.randomUUID());
        items.save(TrackedWorkItem.fresh(opened));
        new ClosureService(items).confirm("OPS-0000000301", ClosureReason.RESOLVED, null, NOW, UUID.randomUUID());
        var loaded = items.findByItemNo("OPS-0000000301").orElseThrow();
        assertThat(loaded.item().snapshot().status()).isEqualTo(WorkItemStatus.CLOSED);
        assertThat(loaded.item().snapshot().closureReason()).isEqualTo(ClosureReason.RESOLVED);
    }

    @Test void workerPersistsABlockedProposalAndReleasesTheTask() {
        var fingerprint = Fingerprint.of("PROD", "vm-worker", "DISK_USAGE_HIGH", Map.of("mountpoint", "/tmp"));
        var opened = store.ingest(alert(fingerprint), NOW, null);
        assertThat(opened.openedCase()).isTrue();
        var worker = new WorkQueueWorker(store, items, new WorkItemCoordinator(), new UnavailableLlm(), new RejectingExecution(),
                new JdbcResourceLock(store), new FixCatalog(List.of(), List.of(), List.of()), 1);
        assertThat(worker.tick(NOW)).isEqualTo(1);
        var loaded = items.findByItemId(opened.itemId()).orElseThrow();
        assertThat(loaded.item().snapshot().status()).isEqualTo(WorkItemStatus.PROPOSING);
        assertThat(loaded.item().snapshot().blockedReason()).isEqualTo(BlockedReason.LLM_UNAVAILABLE);
        assertThat(store.taskCount(opened.itemId(), "DONE")).isEqualTo(1);
        assertThat(store.readyCount(opened.itemId())).isZero();
    }

    @Test void jiraOutboxRetriesA503ThenStopsOnSuccess() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rest/api/2/issue", exchange -> {
            int status = calls.incrementAndGet() == 1 ? 503 : 201;
            byte[] body = status == 201 ? "{\"key\":\"OPS-9\"}".getBytes() : new byte[0];
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            store.postponeSyncs(NOW.plus(Duration.ofDays(30)));
            var item = store.insertWaitingItem("OPS-jira-http", "vm-jira-http");
            var request = store.enqueueSync(item, "jira-http:" + item, NOW);
            var dispatcher = new JiraOutboxDispatcher(store, new JiraHttpGateway("http://127.0.0.1:" + server.getAddress().getPort()));
            assertThat(dispatcher.dispatchDue(NOW, 5)).isEqualTo(1);
            assertThat(store.syncStatus(request)).isEqualTo("RETRY_WAIT");
            assertThat(dispatcher.dispatchDue(NOW.plus(Duration.ofMinutes(2)), 5)).isEqualTo(1);
            assertThat(store.syncStatus(request)).isEqualTo("SUCCEEDED");
            assertThat(calls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    private static javax.sql.DataSource storeDataSource() {
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        return new com.zaxxer.hikari.HikariDataSource(config);
    }

    private static CanonicalAlert alert(String fingerprint) {
        return new CanonicalAlert("1.0", UUID.randomUUID(), SourceType.PROMETHEUS_ALERTMANAGER, "src-flow",
                "alertmanager-v4@1", "evt-worker", "src-fp", fingerprint, Fingerprint.VERSION, "vm-worker", "VM", true, "PROD",
                List.of("svc-worker"), "DISK_USAGE_HIGH", "code", Map.of("mountpoint", "/tmp"), Severity.WARNING,
                SignalStatus.FIRING, NOW, NOW, null, NOW, "worker disk", UUID.randomUUID());
    }
}
