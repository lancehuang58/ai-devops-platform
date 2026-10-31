package com.delta.dms.ops.aidevopsplatform.web.chat;
import com.delta.dms.ops.aidevopsplatform.service.port.in.ChatUseCase;
import com.delta.dms.ops.aidevopsplatform.service.chat.ChatReply;
import com.delta.dms.ops.aidevopsplatform.web.config.PlatformWebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(ChatPageController.class)
@ActiveProfiles("platform")
@Import({PlatformWebSecurityConfiguration.class, ChatSecurityTest.Security.class})
class ChatSecurityTest {
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class Security {}
    @Autowired MockMvc mvc;
    @MockitoBean ChatUseCase chat;
    @MockitoBean ClientRegistrationRepository registrations;
    @Test void anonymousRequestsAreRedirectedToLogin() throws Exception {
        mvc.perform(get("/chat")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(chat);
    }
    @Test void authenticatedPostsRequireCsrf() throws Exception {
        mvc.perform(post("/chat").with(user("operator")).param("message", "hi")).andExpect(status().isForbidden());
        mvc.perform(post("/chat/clear").with(user("operator"))).andExpect(status().isForbidden());
        verifyNoInteractions(chat);
    }
    @Test void htmxRendersEscapedContentAndPreservesCsrfForTheNextMessage() throws Exception {
        when(chat.reply(anyString(), anyList())).thenReturn(new ChatReply("INCIDENT", "HTTP 503", "<script>alert(1)</script>"));
        mvc.perform(get("/chat").with(user("operator"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("/vendor/htmx/htmx.min.js")));
        mvc.perform(post("/chat").with(user("operator")).with(csrf()).header("HX-Request", "true").param("message", "jenkins-prod"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(not(containsString("<html"))));
    }
}
