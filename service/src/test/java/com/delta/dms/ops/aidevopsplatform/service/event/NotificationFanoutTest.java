package com.delta.dms.ops.aidevopsplatform.service.event;

import com.delta.dms.ops.aidevopsplatform.model.alert.PendingNotice;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFanoutTest {
    @Test void eachPendingNoticeIsSentThenMarked() {
        var sent = new ArrayList<String>();
        var marked = new ArrayList<String>();
        new NotificationFanout().deliver(List.of(new PendingNotice("rule:PROD:vm", "one case")),
                (key, body) -> sent.add(key + " " + body), marked::add);
        assertThat(sent).containsExactly("rule:PROD:vm one case");
        assertThat(marked).containsExactly("rule:PROD:vm");
    }
}
