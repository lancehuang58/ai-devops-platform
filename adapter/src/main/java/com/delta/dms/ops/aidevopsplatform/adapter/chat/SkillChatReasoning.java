package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ChatReasoning;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;

public final class SkillChatReasoning implements ChatReasoning {
    private final ChatModel model;
    private final String skill;
    private final String resources;
    public SkillChatReasoning(ChatModel model, String skill, List<String> resources) {
        if (skill == null || skill.isBlank()) throw new IllegalArgumentException("skill is required");
        this.model = model; this.skill = skill; this.resources = String.join(",", resources);
    }
    public String complete(String phase, String context) {
        String instruction = phase.equals("analyse")
                ? "只輸出一行 TYPE|resourceId|tools。TYPE 為 INCIDENT、REQUIREMENT、DISCUSSION；tools 為 catalog,status,health 的逗號清單。無法識別目標時 resourceId 與 tools 留空。可用資源：" + resources
                : "以繁體中文列出診斷、證據來源、未知事項與建議下一步。證據無法確認時明說。只建議修復，絕不聲稱已執行。";
        return model.call(new Prompt(List.of(new SystemMessage(skill + "\n" + instruction),
                new UserMessage(context)))).getResult().getOutput().getText();
    }
    @Override public String toString() { return "SkillChatReasoning[REDACTED]"; }
}
