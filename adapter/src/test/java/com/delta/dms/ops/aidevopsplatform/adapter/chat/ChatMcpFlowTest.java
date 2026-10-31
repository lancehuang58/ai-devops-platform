package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import com.delta.dms.ops.aidevopsplatform.model.catalog.*;
import com.delta.dms.ops.aidevopsplatform.service.chat.ChatService;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ChatMcpFlowTest {
    static ServiceCatalog catalog() {
        return new ServiceCatalog(List.of(), List.of(new MonitoredService("jenkins-prod", "jenkins", "prod", null,
                null, null, null, "${JENKINS_TOKEN}", List.of(), null, Map.of())), List.of());
    }
    @Test void skillToRealInProcessMcpToAnswerUsingStubChatModel() {
        var count = new AtomicInteger();
        ChatModel fake = prompt -> {
            assertThat(prompt.getInstructions().getFirst().getText()).contains("需求分析", "禁止執行修復");
            String result;
            if (count.getAndIncrement() == 0) result = "INCIDENT|jenkins-prod|catalog,status";
            else {
                assertThat(prompt.getContents()).contains("verdict=UNHEALTHY", "type=jenkins").doesNotContain("JENKINS_TOKEN");
                result = "診斷：監控顯示異常；建議人工確認，未執行修復。";
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(result))));
        };
        try (var runtime = new ChatMcpRuntime(new ReadOnlyCatalogTools(catalog(), id -> "verdict=UNHEALTHY"))) {
            var reasoning = new SkillChatReasoning(fake, "需求分析：禁止執行修復", List.of("jenkins-prod"));
            var service = new ChatService(reasoning, runtime.tools(Set.of("jenkins-prod"), Map.of()));
            assertThat(service.reply("jenkins-prod 故障", List.of()).answer()).contains("建議人工確認");
            assertThat(count.get()).isEqualTo(2);
            assertThat(runtime.client().listTools().tools()).hasSize(3);
            assertThatThrownBy(() -> runtime.tools(Set.of("jenkins-prod"), Map.of()).read("shell", "jenkins-prod"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void literalAndUnresolvedKeysAreRejectedAndSettingsAreMasked() {
        assertThatThrownBy(() -> ChatSettings.load("api-key: literal", n -> "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatSettings.load("api-key: ${QWEN_API_KEY}", n -> null)).isInstanceOf(IllegalArgumentException.class);
        var settings = ChatSettings.load("api-key: ${QWEN_API_KEY}", n -> "test-only-value");
        assertThat(settings.model()).isEqualTo("qwen-flash 3.8");
        assertThat(settings.toString()).doesNotContain("test-only-value");
    }
}
