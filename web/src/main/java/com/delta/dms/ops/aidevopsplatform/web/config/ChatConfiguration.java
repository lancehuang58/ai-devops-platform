package com.delta.dms.ops.aidevopsplatform.web.config;
import com.delta.dms.ops.aidevopsplatform.adapter.chat.*;
import com.delta.dms.ops.aidevopsplatform.adapter.catalog.LoadedCatalog;
import com.delta.dms.ops.aidevopsplatform.service.chat.ChatService;
import com.delta.dms.ops.aidevopsplatform.service.chat.ChatReply;
import com.delta.dms.ops.aidevopsplatform.service.port.in.ChatUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@Profile("platform")
public class ChatConfiguration {
    @Bean
    @ConditionalOnProperty(name = "platform.chat.enabled", havingValue = "false", matchIfMissing = true)
    ChatUseCase disabledChat() {
        return (message, history) -> new ChatReply("", "", "AI 對話尚未啟用，請由管理者設定 platform.chat.enabled 與模型連線。");
    }
    @Configuration(proxyBeanMethods = false)
    @Profile("platform")
    @ConditionalOnProperty(name = "platform.chat.enabled", havingValue = "true")
    static class Enabled {
        @Bean ChatSettings chatSettings(Environment env, @Value("${platform.chat.file:config/ai-chat.yml}") String file) throws java.io.IOException {
            return ChatSettings.load(Files.readString(Path.of(file)), env::getProperty);
        }
        @Bean(destroyMethod = "close") ChatMcpRuntime chatMcpRuntime(LoadedCatalog catalog, DataSource dataSource) {
            var jdbc = new JdbcTemplate(dataSource);
            return new ChatMcpRuntime(new ReadOnlyCatalogTools(catalog.catalog(), id -> {
                var results = jdbc.query("SELECT channel, verdict, disposition, observed_at FROM probe_result WHERE instance_id = ? ORDER BY observed_at DESC LIMIT 4",
                        (rs, row) -> "channel=" + rs.getString(1) + ";verdict=" + rs.getString(2) + ";disposition=" + rs.getString(3) + ";observedAt=" + rs.getString(4), id);
                return results.isEmpty() ? "尚無監控探測紀錄，健康未知" : String.join("\n", results);
            }));
        }
        @Bean ChatUseCase chatUseCase(ChatSettings settings, LoadedCatalog catalog, ChatMcpRuntime runtime,
                                     ResourceLoader loader, @Value("${platform.chat.skill:classpath:skills/requirement-analysis/SKILL.md}") String skillLocation) throws java.io.IOException {
            String skill;
            try (var input = loader.getResource(skillLocation).getInputStream()) { skill = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            var resources = catalog.catalog().monitored().stream().map(s -> s.id()).toList();
            return new ChatService(QwenChatFactory.create(settings, skill, resources),
                    runtime.tools(resources.stream().collect(Collectors.toSet()), runtime.connect(settings.servers())));
        }
    }
}
