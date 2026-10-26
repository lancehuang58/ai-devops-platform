package com.delta.dms.ops.aidevopsplatform.adapter.persistence;

import com.delta.dms.ops.aidevopsplatform.model.alert.CanonicalAlert;
import com.delta.dms.ops.aidevopsplatform.model.alert.Fingerprint;
import com.delta.dms.ops.aidevopsplatform.model.alert.GroupStatus;
import com.delta.dms.ops.aidevopsplatform.model.alert.Severity;
import com.delta.dms.ops.aidevopsplatform.model.alert.SignalStatus;
import com.delta.dms.ops.aidevopsplatform.model.alert.SourceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformPersistenceTest {
    static PostgreSQLContainer postgres;
    static JdbcPlatformStore store;
    static final Instant NOW = Instant.parse("2026-10-26T08:00:00Z");

    @BeforeAll
    static void database() {
        postgres = new PostgreSQLContainer("postgres:16-alpine");
        postgres.start();
        store = JdbcPlatformStore.migrateAndOpen(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test void parallelFiringOfOneFingerprintOpensASingleTicket() throws Exception {
        var fingerprint = fingerprint("vm-parallel", "DISK_USAGE_HIGH", Map.of("mountpoint", "/var"));
        int threads = 12;
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        var opened = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                int n = i;
                pool.submit(() -> {
                    ready.countDown();
                    try { go.await(); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                    var result = store.ingest(alert("src-a", "evt-" + n, fingerprint, "vm-parallel", SignalStatus.FIRING, Severity.MAJOR, NOW.plusSeconds(n)), NOW.plusSeconds(n), null);
                    if (result.openedCase()) opened.incrementAndGet();
                });
            }
            ready.await();
            go.countDown();
        }
        assertThat(opened).hasValue(1);
        assertThat(store.countOpenGroups(fingerprint)).isEqualTo(1);
        assertThat(store.countOpenWorkItemsForResource("vm-parallel")).isEqualTo(1);
        assertThat(store.occurrenceCount(fingerprint)).isEqualTo(threads);
    }

    @Test void repeatedSourceEventIsIdempotent() {
        var fingerprint = fingerprint("vm-idem", "DISK_USAGE_HIGH", Map.of());
        var alert = alert("src-a", "same-event", fingerprint, "vm-idem", SignalStatus.FIRING, Severity.WARNING, NOW);
        var first = store.ingest(alert, NOW, null);
        var second = store.ingest(alert, NOW.plusSeconds(5), null);
        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.openedCase()).isFalse();
        assertThat(store.countOpenWorkItemsForResource("vm-idem")).isEqualTo(1);
        assertThat(store.occurrenceCount(fingerprint)).isEqualTo(1);
    }

    @Test void flappingAndReopenStayOnTheOriginalGroup() {
        var fingerprint = fingerprint("vm-flap", "SERVICE_UNAVAILABLE", Map.of());
        store.ingest(alert("src-a", "f1", fingerprint, "vm-flap", SignalStatus.FIRING, Severity.MAJOR, NOW), NOW, null);
        store.ingest(alert("src-a", "r1", fingerprint, "vm-flap", SignalStatus.RESOLVED, Severity.MAJOR, NOW.plusSeconds(60)), NOW.plusSeconds(60), null);
        store.ingest(alert("src-a", "f2", fingerprint, "vm-flap", SignalStatus.FIRING, Severity.MAJOR, NOW.plusSeconds(120)), NOW.plusSeconds(120), null);
        store.ingest(alert("src-a", "r2", fingerprint, "vm-flap", SignalStatus.RESOLVED, Severity.MAJOR, NOW.plusSeconds(180)), NOW.plusSeconds(180), null);
        assertThat(store.groupStatus(fingerprint)).isEqualTo(GroupStatus.RECOVERING);
        assertThat(store.isFlapping(fingerprint)).isTrue();
        assertThat(store.countOpenWorkItemsForResource("vm-flap")).isEqualTo(1);
        store.sweepRecovering(NOW.plusSeconds(180).plus(Duration.ofMinutes(15)));
        assertThat(store.groupStatus(fingerprint)).isEqualTo(GroupStatus.RECOVERED);
        store.ingest(alert("src-a", "f3", fingerprint, "vm-flap", SignalStatus.FIRING, Severity.CRITICAL, NOW.plus(Duration.ofMinutes(10))), NOW.plus(Duration.ofMinutes(10)), null);
        assertThat(store.countOpenGroups(fingerprint)).isEqualTo(1);
        assertThat(store.countOpenWorkItemsForResource("vm-flap")).isEqualTo(1);
    }

    @Test void correlatedAlertsShareOneNotificationUntilConfirmed() {
        var upstream = new CorrelationBinding("storage-deps", "PROD", "storage-1", "vm-a");
        var downstream = new CorrelationBinding("storage-deps", "PROD", "storage-1", "vm-b");
        var first = store.ingest(alert("src-a", "c1", fingerprint("vm-a", "STORAGE_UNRESPONSIVE", Map.of()), "vm-a", SignalStatus.FIRING, Severity.CRITICAL, NOW), NOW, upstream);
        var second = store.ingest(alert("src-a", "c2", fingerprint("vm-b", "SERVICE_UNAVAILABLE", Map.of()), "vm-b", SignalStatus.FIRING, Severity.MAJOR, NOW.plusSeconds(10)), NOW.plusSeconds(10), downstream);
        assertThat(first.openedCase()).isFalse();
        assertThat(second.openedCase()).isFalse();
        assertThat(first.candidateId()).isEqualTo(second.candidateId());
        assertThat(store.notificationCount("storage-deps:PROD:storage-1")).isEqualTo(1);
        assertThat(store.countOpenWorkItemsForResource("vm-a")).isZero();
        store.releaseDueHolds(NOW.plusSeconds(70));
        assertThat(store.countOpenWorkItemsForResource("vm-a")).isZero();
        var item = store.confirmCandidate(first.candidateId(), NOW.plusSeconds(80));
        assertThat(item).isNotNull();
        assertThat(store.workItemsForCandidate(first.candidateId())).isEqualTo(1);
    }

    @Test void singleCorrelatedAlertOpensAfterSixtySeconds() {
        var binding = new CorrelationBinding("db-deps", "PROD", "db-1", "vm-only");
        var fingerprint = fingerprint("vm-only", "SERVICE_UNAVAILABLE", Map.of());
        var held = store.ingest(alert("src-a", "only", fingerprint, "vm-only", SignalStatus.FIRING, Severity.MAJOR, NOW), NOW, binding);
        assertThat(held.openedCase()).isFalse();
        store.releaseDueHolds(NOW.plusSeconds(59));
        assertThat(store.countOpenWorkItemsForResource("vm-only")).isZero();
        store.releaseDueHolds(NOW.plusSeconds(60));
        assertThat(store.countOpenWorkItemsForResource("vm-only")).isEqualTo(1);
        assertThat(store.syncRequestsForResource("vm-only")).isEqualTo(1);
    }

    @Test void skipLockedClaimsDistinctTasksAndOneClaimPerItem() throws Exception {
        var itemA = store.insertWaitingItem("OPS-lock-a", "vm-tasks-a");
        var itemB = store.insertWaitingItem("OPS-lock-b", "vm-tasks-b");
        store.enqueueTask(itemA, "advance-a", NOW);
        store.enqueueTask(itemA, "advance-a-2", NOW);
        store.enqueueTask(itemB, "advance-b", NOW);
        var first = store.claimNext("worker-1", NOW, Duration.ofMinutes(2));
        var second = store.claimNext("worker-2", NOW, Duration.ofMinutes(2));
        var third = store.claimNext("worker-3", NOW, Duration.ofMinutes(2));
        assertThat(List.of(first.orElseThrow().itemId(), second.orElseThrow().itemId())).containsExactlyInAnyOrder(itemA, itemB);
        assertThat(third).isEmpty();
        assertThat(store.claimedCount(itemA)).isEqualTo(1);
        assertThat(store.readyCount(itemA)).isEqualTo(1);
    }

    @Test void parallelClaimsDoNotDoubleBookATask() throws Exception {
        for (int i = 0; i < 8; i++) {
            var item = store.insertWaitingItem("OPS-par-" + i, "vm-par-" + i);
            store.enqueueTask(item, "job-" + i, NOW);
        }
        var claims = new java.util.concurrent.ConcurrentLinkedQueue<UUID>();
        var ready = new CountDownLatch(8);
        var go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                int n = i;
                pool.submit(() -> {
                    ready.countDown();
                    try { go.await(); } catch (InterruptedException e) { throw new IllegalStateException(e); }
                    store.claimNext("w-" + n, NOW, Duration.ofMinutes(2)).ifPresent(claim -> claims.add(claim.taskId()));
                });
            }
            ready.await();
            go.countDown();
        }
        assertThat(claims).hasSize(8).doesNotHaveDuplicates();
    }

    @Test void resourceLockIsExclusiveAndAnInFlightLeaseDoesNotAutoRelease() {
        var holder = store.insertWaitingItem("OPS-hold", "vm-lock");
        var waiter = store.insertWaitingItem("OPS-wait", "vm-lock");
        assertThat(store.tryAcquire(holder, "vm-lock", "QUEUED", "app-1", 1, NOW, Duration.ofMinutes(2))).isTrue();
        assertThat(store.tryAcquire(waiter, "vm-lock", "QUEUED", "app-2", 1, NOW, Duration.ofMinutes(2))).isFalse();
        assertThat(store.blockedReason(waiter)).isEqualTo("RESOURCE_LOCK_WAIT");
        store.expireLocks(NOW.plus(Duration.ofMinutes(3)));
        assertThat(store.tryAcquire(waiter, "vm-lock", "QUEUED", "app-2", 2, NOW.plus(Duration.ofMinutes(3)), Duration.ofMinutes(2))).isTrue();

        var running = store.insertWaitingItem("OPS-run", "vm-inflight");
        var other = store.insertWaitingItem("OPS-other", "vm-inflight");
        assertThat(store.tryAcquire(running, "vm-inflight", "RUNNING", "app-1", 1, NOW, Duration.ofSeconds(1))).isTrue();
        store.expireLocks(NOW.plusSeconds(30));
        assertThat(store.lockHeld("vm-inflight")).isTrue();
        assertThat(store.tryAcquire(other, "vm-inflight", "QUEUED", "app-2", 1, NOW.plusSeconds(30), Duration.ofMinutes(2))).isFalse();
    }

    @Test void jiraSyncStopsAfterFiveRetries() {
        var item = store.insertWaitingItem("OPS-jira", "vm-jira");
        var request = store.enqueueSync(item, "create:" + item, NOW);
        for (int attempt = 1; attempt <= 5; attempt++) {
            var claimed = store.claimSync(NOW.plus(Duration.ofDays(attempt)), "sync");
            assertThat(claimed).isPresent();
            var progress = store.failSync(request, NOW.plus(Duration.ofDays(attempt)), 0.0);
            assertThat(progress.stop()).isFalse();
        }
        var last = store.claimSync(NOW.plus(Duration.ofDays(10)), "sync");
        assertThat(last).isPresent();
        var stopped = store.failSync(request, NOW.plus(Duration.ofDays(10)), 0.0);
        assertThat(stopped.stop()).isTrue();
        assertThat(store.claimSync(NOW.plus(Duration.ofDays(30)), "sync")).isEmpty();
        assertThat(store.syncStatus(request)).isEqualTo("PENDING_HUMAN");
        assertThat(store.syncAttempts(request)).isEqualTo(6);
    }

    private static String fingerprint(String resource, String symptom, Map<String, String> discriminator) {
        return Fingerprint.of("PROD", resource, symptom, discriminator);
    }

    private static CanonicalAlert alert(String sourceId, String sourceEventId, String fingerprint, String resource,
                                        SignalStatus status, Severity severity, Instant observed) {
        return new CanonicalAlert("1.0", UUID.randomUUID(), SourceType.PROMETHEUS_ALERTMANAGER, sourceId, "alertmanager-v4@1",
                sourceEventId, "src-fp", fingerprint, Fingerprint.VERSION, resource, "VM", true, "PROD",
                List.of("svc-" + resource), status == SignalStatus.FIRING ? "DISK_USAGE_HIGH" : "DISK_USAGE_HIGH",
                "code", Map.of(), severity, status, observed, observed, null, observed, "disk", UUID.randomUUID());
    }
}
