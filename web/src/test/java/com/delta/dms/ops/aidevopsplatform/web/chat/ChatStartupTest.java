package com.delta.dms.ops.aidevopsplatform.web.chat;
import com.delta.dms.ops.aidevopsplatform.web.config.ChatConfiguration;
import com.delta.dms.ops.aidevopsplatform.service.port.in.ChatUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class ChatStartupTest {
    @Test void disabledChatNeedsNoModelCredentialsOrMcpConnections() {
        new ApplicationContextRunner().withUserConfiguration(ChatConfiguration.class)
                .withPropertyValues("spring.profiles.active=platform")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ChatUseCase.class);
                    assertThat(context.getBean(ChatUseCase.class).reply("hi", java.util.List.of()).answer()).contains("尚未啟用");
                });
    }
    @Test void enablingFlagAloneDoesNotActivateOutsidePlatformProfile() {
        new ApplicationContextRunner().withUserConfiguration(ChatConfiguration.class)
                .withPropertyValues("platform.chat.enabled=true")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ChatUseCase.class));
    }
    @Test void defaultProfileDoesNotCreateChatBeans() {
        new ApplicationContextRunner().withUserConfiguration(ChatConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ChatUseCase.class));
    }
}
