package com.delta.dms.ops.aidevopsplatform.adapter.chat;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ChatReasoning;
import java.time.Duration;
import java.util.List;

public final class QwenChatFactory {
    private QwenChatFactory() {}
    public static ChatReasoning create(ChatSettings settings, String skill, List<String> resources) {
        var options = OpenAiChatOptions.builder().model(settings.model()).baseUrl(settings.baseUrl()).apiKey(settings.apiKey())
                .timeout(Duration.ofSeconds(30)).maxRetries(0).maxCompletionTokens(2000).build();
        var delegate = new SkillChatReasoning(OpenAiChatModel.builder().options(options).build(), skill, resources);
        return (phase, context) -> {
            var safe = context.replace(settings.apiKey(), "[REDACTED]");
            for (var remote : settings.servers()) if (remote.token() != null) safe = safe.replace(remote.token(), "[REDACTED]");
            var result = delegate.complete(phase, safe).replace(settings.apiKey(), "[REDACTED]");
            for (var remote : settings.servers()) if (remote.token() != null) result = result.replace(remote.token(), "[REDACTED]");
            return result;
        };
    }
}
