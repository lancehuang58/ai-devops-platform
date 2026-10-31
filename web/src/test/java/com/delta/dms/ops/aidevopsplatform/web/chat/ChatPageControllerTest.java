package com.delta.dms.ops.aidevopsplatform.web.chat;
import com.delta.dms.ops.aidevopsplatform.service.chat.ChatReply;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import static org.assertj.core.api.Assertions.*;

class ChatPageControllerTest {
    @Test void sessionHistoryIsBoundedAndIsolatedAndClearable() {
        var controller = new ChatPageController((message, history) -> new ChatReply("INCIDENT", "來源", "回答"));
        var first = new MockHttpSession();
        for (int i = 0; i < 25; i++) controller.send("message " + i, first, new ExtendedModelMap(), "true");
        var model = new ExtendedModelMap();
        controller.page(first, model);
        assertThat((java.util.List<?>) model.get("turns")).hasSize(20);
        var second = new ExtendedModelMap();
        controller.page(new MockHttpSession(), second);
        assertThat((java.util.List<?>) second.get("turns")).isEmpty();
        controller.clear(first);
        controller.page(first, model);
        assertThat((java.util.List<?>) model.get("turns")).isEmpty();
    }
    @Test void validationErrorIsSafeAndDoesNotStoreTheRejectedTurn() {
        var controller = new ChatPageController((message, history) -> { throw new IllegalArgumentException("bad"); });
        var model = new ExtendedModelMap();
        assertThat(controller.send("bad", new MockHttpSession(), model, "true")).isEqualTo("platform/chat :: conversation");
        assertThat((java.util.List<?>) model.get("turns")).isEmpty();
        assertThat(model.get("error")).isNotNull();
    }
}
