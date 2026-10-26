package com.delta.dms.ops.aidevopsplatform.web.platform;

import com.delta.dms.ops.aidevopsplatform.model.workitem.ClosureReason;
import com.delta.dms.ops.aidevopsplatform.model.workitem.WorkItemStatus;
import com.delta.dms.ops.aidevopsplatform.service.port.out.WorkItemQuery;
import com.delta.dms.ops.aidevopsplatform.service.takeover.TakeoverService;
import com.delta.dms.ops.aidevopsplatform.service.workflow.ClosureService;
import com.delta.dms.ops.aidevopsplatform.service.workflow.TimelineEvent;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemDetail;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemListQuery;
import com.delta.dms.ops.aidevopsplatform.service.workflow.WorkItemSummary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.UUID;

@Controller
@Profile("platform")
public class WorkItemPageController {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter TAIPEI_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(TAIPEI);
    private final WorkItemQuery query;
    private final ClosureService closures;
    private final TakeoverService takeovers;
    private final Clock clock;

    public WorkItemPageController(WorkItemQuery query, ClosureService closures, TakeoverService takeovers, Clock clock) {
        this.query = query;
        this.closures = closures;
        this.takeovers = takeovers;
        this.clock = clock;
    }

    @GetMapping("/")
    public String root() { return "redirect:/work-items"; }

    @GetMapping("/home")
    public String home() { return "redirect:/work-items"; }

    @GetMapping("/login")
    public String login() { return "platform/login"; }

    @GetMapping("/login-failure")
    public String loginFailure() { return "login-failure"; }

    @GetMapping("/work-items")
    public String list(@RequestParam(required = false) String system,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "50") int size,
                       Model model) {
        int pageSize = size == 20 || size == 100 ? size : 50;
        int pageNumber = Math.max(page, 1);
        boolean all = "ALL".equals(status);
        String exact = all || status == null || status.isBlank() ? null : status;
        var result = query.search(new WorkItemListQuery(blankToNull(system), parseTaipei(from), parseTaipei(to),
                exact, exact == null && !all, pageNumber, pageSize));
        model.addAttribute("rows", result.items().stream().map(this::row).toList());
        model.addAttribute("page", result.page());
        model.addAttribute("size", result.size());
        model.addAttribute("totalPages", result.totalPages());
        model.addAttribute("total", result.totalElements());
        model.addAttribute("system", system == null ? "" : system);
        model.addAttribute("from", from == null ? "" : from);
        model.addAttribute("to", to == null ? "" : to);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("statuses", Arrays.stream(WorkItemStatus.values()).map(Enum::name).toList());
        return "platform/work-items";
    }

    @GetMapping("/work-items/{itemNo}")
    public String detail(@PathVariable String itemNo, Model model) {
        var item = query.findByItemNo(itemNo).orElseThrow(() -> new IllegalArgumentException("work item not found"));
        model.addAttribute("item", item);
        model.addAttribute("updatedAt", TAIPEI_FORMAT.format(item.updatedAt()));
        model.addAttribute("timeline", item.timeline().stream().map(this::event).toList());
        model.addAttribute("reasons", Arrays.stream(ClosureReason.values())
                .filter(reason -> reason != ClosureReason.FORCED_BY_COMMERCIAL_MODEL).map(Enum::name).toList());
        return "platform/work-item";
    }

    @PostMapping("/work-items/{itemNo}/closure")
    public String confirm(@PathVariable String itemNo, @RequestParam String reason, @RequestParam(required = false) String duplicateOf) {
        try {
            var closure = ClosureReason.valueOf(reason);
            var duplicate = duplicateOf == null || duplicateOf.isBlank() ? null : UUID.fromString(duplicateOf.trim());
            closures.confirm(itemNo, closure, duplicate, clock.instant(), UUID.randomUUID());
            return "redirect:/work-items/" + itemNo;
        } catch (RuntimeException ex) {
            return "redirect:/work-items/" + itemNo + "?error=closure";
        }
    }

    @PostMapping("/work-items/{itemNo}/takeover")
    public String takeover(@PathVariable String itemNo, @RequestParam String tool, @RequestParam String modelName,
                           @RequestParam String reason, Principal principal) {
        try {
            takeovers.start(itemNo, principal.getName(), tool, modelName, reason, clock.instant(), UUID.randomUUID());
            return "redirect:/work-items/" + itemNo;
        } catch (RuntimeException ex) {
            return "redirect:/work-items/" + itemNo + "?error=takeover";
        }
    }

    @GetMapping("/work-items/{itemNo}/package")
    public ResponseEntity<byte[]> export(@PathVariable String itemNo, @RequestParam(defaultValue = "") String evidence, Principal principal) {
        var body = takeovers.exportPackage(itemNo, evidence, principal.getName(), clock.instant());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + itemNo + "-takeover.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @PostMapping("/work-items/{itemNo}/write-back")
    public String writeBack(@PathVariable String itemNo, @RequestParam String summary, @RequestParam String rootCause,
                            @RequestParam String actions, @RequestParam String evidence, Principal principal) {
        try {
            takeovers.writeBack(itemNo, summary, rootCause, actions, evidence, principal.getName(), clock.instant());
            return "redirect:/work-items/" + itemNo;
        } catch (RuntimeException ex) {
            return "redirect:/work-items/" + itemNo + "?error=writeback";
        }
    }

    @PostMapping("/work-items/{itemNo}/force-close")
    public String forceClose(@PathVariable String itemNo, @RequestParam(defaultValue = "false") boolean firingAcknowledged, Principal principal) {
        try {
            takeovers.forceClose(itemNo, principal.getName(), firingAcknowledged, clock.instant(), UUID.randomUUID());
            return "redirect:/work-items/" + itemNo;
        } catch (RuntimeException ex) {
            return "redirect:/work-items/" + itemNo + "?error=force";
        }
    }

    private ViewRow row(WorkItemSummary item) {
        return new ViewRow(item.itemNo(), item.itemType(), item.status(), item.title(), item.environment(),
                item.resourceId() == null ? "" : item.resourceId(), TAIPEI_FORMAT.format(item.updatedAt()));
    }

    private ViewEvent event(TimelineEvent event) {
        return new ViewEvent(event.sequence(), event.type(), event.detail() == null ? "" : event.detail(), TAIPEI_FORMAT.format(event.occurredAt()));
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static java.time.Instant parseTaipei(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value).atZone(TAIPEI).toInstant(); }
        catch (DateTimeParseException ex) { return null; }
    }

    public record ViewRow(String itemNo, String itemType, String status, String title, String environment, String resourceId, String updatedAt) {}
    public record ViewEvent(int sequence, String type, String detail, String occurredAt) {}
}
