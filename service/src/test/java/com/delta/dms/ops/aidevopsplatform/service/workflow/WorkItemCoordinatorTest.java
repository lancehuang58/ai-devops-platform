package com.delta.dms.ops.aidevopsplatform.service.workflow;

import com.delta.dms.ops.aidevopsplatform.model.remediation.AutoRemediationRule;
import com.delta.dms.ops.aidevopsplatform.model.remediation.MatchContext;
import com.delta.dms.ops.aidevopsplatform.model.remediation.PublishedSolution;
import com.delta.dms.ops.aidevopsplatform.model.workitem.BlockedReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ClosureReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ItemType;
import com.delta.dms.ops.aidevopsplatform.model.workitem.SourceKind;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItem;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemStatus;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ChangeExecutionPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.LlmPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ResourceLockPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-10-26T09:00:00Z");
    private final WorkItemCoordinator coordinator = new WorkItemCoordinator();
    private final LlmPort llm = new FixedLlm(true);
    private final ChangeExecutionPort execution = new ChangeExecutionPort() {
        public boolean apply(com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot item) { return true; }
        public boolean verify(com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemSnapshot item) { return true; }
    };
    private final ResourceLockPort locks = item -> true;

    @Test void whitelistMatchSkipsApprovalAndStillWaitsForClosureConfirmation() {
        var item = incident();
        var catalog = new FixCatalog(List.of(new PublishedSolution("disk-cleanup", "1", "DISK_USAGE_HIGH", true)),
                List.of(new AutoRemediationRule("r", "disk-cleanup", "1", "DISK_USAGE_HIGH", "PROD", "vm-1", true, 1, 3, Duration.ofHours(24))),
                List.of());
        step(item, catalog);
        step(item, catalog);
        step(item, catalog);
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.APPLYING);
        step(item, catalog);
        step(item, catalog);
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSURE_REVIEW);
        var version = item.snapshot().rowVersion();
        step(item, catalog);
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSURE_REVIEW);
        assertThat(item.snapshot().rowVersion()).isEqualTo(version);
        item.confirmClosure(ClosureReason.RESOLVED, null, NOW, UUID.randomUUID());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSED);
    }

    @Test void anythingOutsideTheWhitelistStaysPending() {
        var item = incident();
        var catalog = new FixCatalog(List.of(new PublishedSolution("disk-cleanup", "1", "DISK_USAGE_HIGH", true)), List.of(), List.of());
        step(item, catalog);
        step(item, catalog);
        step(item, catalog);
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.PENDING_APPROVAL);
        var version = item.snapshot().rowVersion();
        step(item, catalog);
        assertThat(item.snapshot().rowVersion()).isEqualTo(version);
    }

    @Test void missingFixAsksTheModelThenWaitsAndAnUnavailableModelDoesNotChangeStatus() {
        var item = incident();
        step(item, FixCatalog(false));
        step(item, FixCatalog(false));
        step(item, FixCatalog(false));
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.PROPOSING);
        step(item, FixCatalog(false));
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.PENDING_APPROVAL);
        var blocked = incident();
        blocked.beginCollecting(NOW, UUID.randomUUID());
        blocked.beginMatching(NOW, UUID.randomUUID());
        blocked.noMatch(NOW, UUID.randomUUID());
        coordinator.advance(blocked, match(), CollectedEvidence.ready(), FixCatalog(false), new FixedLlm(false), execution, locks, NOW, UUID.randomUUID());
        assertThat(blocked.snapshot().status()).isEqualTo(WorkItemStatus.PROPOSING);
        assertThat(blocked.snapshot().blockedReason()).isEqualTo(BlockedReason.LLM_UNAVAILABLE);
    }

    private void step(WorkItem item, FixCatalog catalog) {
        coordinator.advance(item, match(), CollectedEvidence.ready(), catalog, llm, execution, locks, NOW, UUID.randomUUID());
    }

    private static MatchContext match() {
        return new MatchContext("DISK_USAGE_HIGH", "PROD", "vm-1", "fp", false);
    }

    private static FixCatalog FixCatalog(boolean published) {
        return new FixCatalog(published ? List.of(new PublishedSolution("disk-cleanup", "1", "DISK_USAGE_HIGH", true)) : List.of(), List.of(), List.of());
    }

    private static WorkItem incident() {
        return WorkItem.open(UUID.randomUUID(), "OPS-0000000099", ItemType.INCIDENT, "disk", "monitor", SourceKind.MONITOR, "PROD", NOW, UUID.randomUUID());
    }

    private static final class FixedLlm implements LlmPort {
        private final boolean up;
        private FixedLlm(boolean up) { this.up = up; }
        public boolean available() { return up; }
        public String draftProposal(WorkItemSnapshot item) { return "restart the service"; }
    }
}
