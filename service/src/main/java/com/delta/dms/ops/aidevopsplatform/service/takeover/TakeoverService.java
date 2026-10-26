package com.delta.dms.ops.aidevopsplatform.service.takeover;

import com.delta.dms.ops.aidevopsplatform.model.alert.Fingerprint;
import com.delta.dms.ops.aidevopsplatform.model.takeover.ExportMasker;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemStatus;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemStore;
import com.delta.dms.ops.aidevopsplatform.service.workflow.TrackedWorkItem;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Manual commercial-model handoff. The platform never calls the commercial model. */
public final class TakeoverService {
    private final WorkItemStore store;
    private final ExportMasker masker = new ExportMasker();

    public TakeoverService(WorkItemStore store) { this.store = store; }

    public UUID start(String itemNo, String operator, String tool, String model, String reason, Instant now, UUID eventId) {
        var tracked = require(itemNo);
        var from = tracked.item().snapshot().status();
        var takeoverId = UUID.randomUUID();
        tracked.item().takeover(takeoverId, now, eventId);
        store.save(tracked);
        store.recordTakeover(takeoverId, tracked.item().snapshot().itemId(), operator, tool, model, reason, from.name(), now);
        return takeoverId;
    }

    public byte[] exportPackage(String itemNo, String evidence, String operator, Instant now) {
        var tracked = require(itemNo);
        if (tracked.item().snapshot().activeTakeoverId() == null) {
            throw new IllegalStateException("takeover package requires an active takeover");
        }
        var maskedEvidence = masker.mask(evidence, operator);
        var item = tracked.item().snapshot();
        var json = """
                {"itemNo":"%s","status":"%s","title":"%s"}
                """.formatted(item.itemNo(), item.status(), item.title());
        var bytes = zip(item.itemNo(), json, maskedEvidence.text());
        store.recordPackage(item.activeTakeoverId(), bytes, Fingerprint.sha256(new String(bytes, StandardCharsets.ISO_8859_1)), operator, now);
        return bytes;
    }

    public void writeBack(String itemNo, String summary, String rootCause, String actions, String evidence, String operator, Instant now) {
        var tracked = require(itemNo);
        var takeoverId = tracked.item().snapshot().activeTakeoverId();
        if (takeoverId == null) throw new IllegalStateException("write-back requires an active takeover");
        store.recordWriteback(takeoverId, masker.mask(summary, operator).text(), masker.mask(rootCause, operator).text(),
                masker.mask(actions, operator).text(), masker.mask(evidence, operator).text(), operator, now);
    }

    public void forceClose(String itemNo, String operator, boolean firingAcknowledged, Instant now, UUID eventId) {
        var tracked = require(itemNo);
        if (tracked.item().snapshot().status() == WorkItemStatus.CLOSED) {
            throw new IllegalStateException("already closed");
        }
        if (!store.hasWriteback(tracked.item().snapshot().itemId())) {
            throw new IllegalStateException("force close requires a write-back");
        }
        if (store.firing(tracked.item().snapshot().itemId()) && !firingAcknowledged) {
            throw new IllegalStateException("firing alert must be acknowledged");
        }
        tracked.item().forceClose(now, eventId);
        store.save(tracked);
    }

    private TrackedWorkItem require(String itemNo) {
        return store.findByItemNo(itemNo).orElseThrow(() -> new IllegalArgumentException("work item not found"));
    }

    private static byte[] zip(String itemNo, String itemJson, String evidence) {
        try {
            var out = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(out)) {
                put(zip, "README.md", "Manual takeover package for " + itemNo + ". Secrets are masked. Do not automate this with an API key.\n");
                put(zip, "item.json", itemJson);
                put(zip, "evidence.txt", evidence);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("takeover package failed", e);
        }
    }

    private static void put(ZipOutputStream zip, String name, String text) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
