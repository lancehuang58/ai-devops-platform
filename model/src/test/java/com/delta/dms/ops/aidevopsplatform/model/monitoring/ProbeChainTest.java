package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProbeChainTest {
    @Test void priorityIsEndpointThenMcpThenApiThenVm() {
        assertThat(ProbeChain.order(List.of(ProbeChannel.VM, ProbeChannel.API, ProbeChannel.ENDPOINT, ProbeChannel.MCP)))
                .containsExactly(ProbeChannel.ENDPOINT, ProbeChannel.MCP, ProbeChannel.API, ProbeChannel.VM);
    }

    @Test void unhealthyAnswerStopsAndRaisesAnIncident() {
        var decision = ProbeChain.decide("jenkins-prod", List.of(
                attempt(ProbeChannel.ENDPOINT, ChannelVerdict.CHANNEL_FAILED, "dns"),
                attempt(ProbeChannel.MCP, ChannelVerdict.UNHEALTHY, "disk 99%"),
                attempt(ProbeChannel.API, ChannelVerdict.HEALTHY, "200")));
        assertThat(decision.disposition()).isEqualTo(ProbeDisposition.INCIDENT);
        assertThat(decision.symptomType()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(decision.producingChannel()).isEqualTo(ProbeChannel.MCP);
        assertThat(decision.summary()).contains("dns", "disk 99%").doesNotContain("200");
        assertThat(decision.dedupKey()).isNull();
    }

    @Test void channelFailureFallsBackAndAHealthyLowerChannelIsOnlyADegradedNotice() {
        var decision = ProbeChain.decide("jenkins-prod", List.of(
                attempt(ProbeChannel.ENDPOINT, ChannelVerdict.CHANNEL_FAILED, "timeout"),
                attempt(ProbeChannel.API, ChannelVerdict.CHANNEL_FAILED, "tls"),
                attempt(ProbeChannel.VM, ChannelVerdict.HEALTHY, "up")));
        assertThat(decision.disposition()).isEqualTo(ProbeDisposition.CHANNEL_DEGRADED);
        assertThat(decision.symptomType()).isNull();
        assertThat(decision.dedupKey()).isEqualTo("channel-degraded:jenkins-prod:ENDPOINT+API");
        assertThat(decision.summary()).contains("timeout", "tls", "VM");
        assertThat(decision.producingChannel()).isEqualTo(ProbeChannel.VM);
    }

    @Test void allConfiguredChannelsFailAndTheIncidentCarriesEveryReason() {
        var decision = ProbeChain.decide("gitlab-prod", List.of(
                attempt(ProbeChannel.ENDPOINT, ChannelVerdict.CHANNEL_FAILED, "connection refused"),
                attempt(ProbeChannel.MCP, ChannelVerdict.CHANNEL_FAILED, "mcp unavailable"),
                attempt(ProbeChannel.API, ChannelVerdict.CHANNEL_FAILED, "tls"),
                attempt(ProbeChannel.VM, ChannelVerdict.CHANNEL_FAILED, "auth failure")));
        assertThat(decision.disposition()).isEqualTo(ProbeDisposition.INCIDENT);
        assertThat(decision.symptomType()).isEqualTo("HEALTH_CHECK_FAILED");
        assertThat(decision.producingChannel()).isNull();
        assertThat(decision.summary()).contains("connection refused", "mcp unavailable", "tls", "auth failure");
        assertThat(decision.dedupKey()).isNull();
    }

    @Test void firstHealthyChannelRaisesNothing() {
        var decision = ProbeChain.decide("jenkins-prod", List.of(
                attempt(ProbeChannel.ENDPOINT, ChannelVerdict.HEALTHY, "200")));
        assertThat(decision.disposition()).isEqualTo(ProbeDisposition.HEALTHY);
        assertThat(decision.dedupKey()).isNull();
        assertThat(decision.producingChannel()).isEqualTo(ProbeChannel.ENDPOINT);
    }

    @Test void degradedKeyDoesNotDependOnTheReasonText() {
        var first = ProbeChain.decide("vm-1", List.of(
                attempt(ProbeChannel.ENDPOINT, ChannelVerdict.CHANNEL_FAILED, "timeout"),
                attempt(ProbeChannel.VM, ChannelVerdict.HEALTHY, "up")));
        var second = ProbeChain.decide("vm-1", List.of(
                attempt(ProbeChannel.ENDPOINT, ChannelVerdict.CHANNEL_FAILED, "connection refused"),
                attempt(ProbeChannel.VM, ChannelVerdict.HEALTHY, "ok")));
        assertThat(first.dedupKey()).isEqualTo("channel-degraded:vm-1:ENDPOINT").isEqualTo(second.dedupKey());
    }

    @Test void noConfiguredChannelIsRejected() {
        assertThatThrownBy(() -> ProbeChain.decide("jenkins-prod", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ChannelAttempt attempt(ProbeChannel channel, ChannelVerdict verdict, String detail) {
        return new ChannelAttempt(channel, verdict, detail);
    }
}
