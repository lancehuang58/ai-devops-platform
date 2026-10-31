package com.delta.dms.ops.aidevopsplatform.web.chat;
import com.delta.dms.ops.aidevopsplatform.service.port.in.ChatUseCase;
import com.delta.dms.ops.aidevopsplatform.model.security.SecretFilter;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.context.annotation.Profile;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
@Profile("platform")
public final class ChatPageController {
    private static final String HISTORY = ChatPageController.class.getName() + ".history";
    public record Turn(String user, String analysis, String evidence, String answer) implements java.io.Serializable {}
    private final ChatUseCase chat;
    public ChatPageController(ChatUseCase chat) { this.chat = chat; }
    @GetMapping("/chat")
    public String page(HttpSession session, Model model) {
        synchronized (session) { model.addAttribute("turns", turns(session)); }
        return "platform/chat";
    }
    @PostMapping("/chat")
    public String send(@RequestParam("message") String message, HttpSession session, Model model,
                       @RequestHeader(value = "HX-Request", required = false) String htmx) {
        synchronized (session) {
            var turns = new ArrayList<>(turns(session));
            try {
                if (message == null || message.isBlank() || message.length() > 4000)
                    throw new IllegalArgumentException("invalid message size");
                var history = turns.stream().flatMap(t -> java.util.stream.Stream.of("使用者：" + t.user(), "助手：" + t.answer())).toList();
                var reply = chat.reply(message, history);
                turns.add(new Turn(SecretFilter.redact(message), reply.analysis(), reply.evidence(), reply.answer()));
                if (turns.size() > 20) turns.removeFirst();
                session.setAttribute(HISTORY, List.copyOf(turns));
            } catch (IllegalArgumentException ex) { model.addAttribute("error", "無法分析需求，請輸入 1～4000 字，並提供目錄中的資源 id。"); }
            model.addAttribute("turns", turns);
        }
        return "true".equals(htmx) ? "platform/chat :: conversation" : "platform/chat";
    }
    @PostMapping("/chat/clear")
    public String clear(HttpSession session) { synchronized (session) { session.removeAttribute(HISTORY); } return "redirect:/chat"; }
    @SuppressWarnings("unchecked")
    private List<Turn> turns(HttpSession session) {
        var value = session.getAttribute(HISTORY);
        return value == null ? List.of() : (List<Turn>) value;
    }
}
