package com.delta.dms.ops.aidevopsplatform.service.takeover;

import com.delta.dms.ops.aidevopsplatform.model.workitem.ClosureReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.ItemType;
import com.delta.dms.ops.aidevopsplatform.model.workitem.SourceKind;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItem;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemStatus;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemStore;
import com.delta.dms.ops.aidevopsplatform.service.workflow.TrackedWorkItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TakeoverServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private final MemoryStore store = new MemoryStore();
    private final TakeoverService service = new TakeoverService(store);

    @Test void packageMasksSecretsAndForceCloseSkipsClosureReview() throws Exception {
        var item = WorkItem.open(UUID.randomUUID(), "OPS-0000000100", ItemType.INCIDENT, "qwen stalled", "ada", SourceKind.USER, "PROD", NOW, UUID.randomUUID());
        item.beginCollecting(NOW, UUID.randomUUID());
        store.items.put(item.snapshot().itemNo(), TrackedWorkItem.fresh(item));
        service.start(item.snapshot().itemNo(), "ada", "CLAUDE_CODE", "claude", "qwen stalled", NOW, UUID.randomUUID());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.PENDING_HUMAN);
        var zip = service.exportPackage(item.snapshot().itemNo(), "password=s3cret on 10.1.2.3", "ada", NOW);
        try (var archive = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            var names = new java.util.ArrayList<String>();
            var text = new StringBuilder();
            java.util.zip.ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                names.add(entry.getName());
                text.append(new String(archive.readAllBytes()));
            }
            assertThat(names).contains("README.md", "item.json", "evidence.txt");
            assertThat(text).doesNotContain("s3cret", "10.1.2.3").contains("[REDACTED:PASSWORD]");
        }
        assertThatThrownBy(() -> service.forceClose(item.snapshot().itemNo(), "ada", true, NOW, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        service.writeBack(item.snapshot().itemNo(), "cleared disk", "log growth", "rotated logs", "df shows 40%", "ada", NOW);
        service.forceClose(item.snapshot().itemNo(), "ada", true, NOW, UUID.randomUUID());
        assertThat(item.snapshot().status()).isEqualTo(WorkItemStatus.CLOSED);
        assertThat(item.snapshot().closureReason()).isEqualTo(ClosureReason.FORCED_BY_COMMERCIAL_MODEL);
    }

    private static final class MemoryStore implements WorkItemStore {
        private final Map<String, TrackedWorkItem> items = new HashMap<>();
        private final Map<UUID, Boolean> writebacks = new HashMap<>();

        public Optional<TrackedWorkItem> findByItemNo(String itemNo) { return Optional.ofNullable(items.get(itemNo)); }
        public Optional<TrackedWorkItem> findByItemId(UUID itemId) {
            return items.values().stream().filter(tracked -> tracked.item().snapshot().itemId().equals(itemId)).findFirst();
        }
        public void save(TrackedWorkItem tracked) { items.put(tracked.item().snapshot().itemNo(), tracked); }
        public void recordTakeover(UUID takeoverId, UUID itemId, String operator, String tool, String model, String reason, String fromStatus, Instant now) {}
        public void recordWriteback(UUID takeoverId, String summary, String rootCause, String actions, String evidence, String operator, Instant now) {
            writebacks.put(takeoverId, true);
        }
        public void recordPackage(UUID takeoverId, byte[] body, String sha256, String operator, Instant now) {}
        public boolean hasWriteback(UUID itemId) {
            return items.values().stream().anyMatch(tracked -> writebacks.containsKey(tracked.item().snapshot().activeTakeoverId()));
        }
        public boolean firing(UUID itemId) { return true; }
    }
}
