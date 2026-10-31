package com.delta.dms.ops.aidevopsplatform.service.chat;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ChatServiceTest {
    @Test void analysesBeforeCollectingAndAnswering() {
        var calls = new AtomicInteger();
        var chat = new ChatService((phase, context) -> {
            if (phase.equals("analyse")) {
                assertThat(calls.getAndIncrement()).isZero();
                return "INCIDENT|jenkins-prod|catalog,status,health";
            }
            assertThat(calls.get()).isEqualTo(4);
            assertThat(context).contains("health=HTTP 503", "INCIDENT");
            return "診斷：服務不健康；建議人工確認。";
        }, (tool, resource) -> {
            assertThat(resource).isEqualTo("jenkins-prod");
            calls.incrementAndGet();
            return tool.equals("health") ? "HTTP 503" : "known";
        });
        assertThat(chat.reply("jenkins-prod 壞了", List.of()).answer()).contains("人工確認");
    }
    @Test void rejectsModelGeneratedWriteToolsBeforeAnyCall() {
        var chat = new ChatService((phase, context) -> "INCIDENT|jenkins-prod|shell", (tool, resource) -> {
            throw new AssertionError("must never dispatch");
        });
        assertThatThrownBy(() -> chat.reply("修復", List.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingTargetRequestsClarificationWithoutTools() {
        var chat = new ChatService((phase, context) -> "DISCUSSION||", (tool, resource) -> {
            throw new AssertionError("no target");
        });
        assertThat(chat.reply("聊聊", List.of()).answer()).contains("目標");
    }
    @Test void boundsInputAndDoesNotExposeExceptionDetails() {
        var chat = new ChatService((phase, context) -> { throw new IllegalStateException("secret"); }, (t, r) -> "");
        assertThatThrownBy(() -> chat.reply("x".repeat(4001), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(chat.reply("hello", List.of()).answer()).doesNotContain("secret").contains("暫時");
    }
}
