package com.delta.dms.ops.aidevopsplatform.service.monitoring;

import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelAttempt;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ChannelVerdict;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeChannel;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeDisposition;
import com.delta.dms.ops.aidevopsplatform.model.monitoring.ProbeTarget;
import com.delta.dms.ops.aidevopsplatform.service.port.out.ApiProbePort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.EndpointProbePort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.GloveProbePort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.McpProbePort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeRunnerTest {
    @Test void unhealthyServiceStopsTheChain() {
        var calls = new ArrayList<ProbeChannel>();
        var decision = new ProbeRunner().check(target(ProbeChannel.ENDPOINT, ProbeChannel.MCP, ProbeChannel.API),
                endpoint(calls, ChannelVerdict.UNHEALTHY, "HTTP 503"),
                mcp(calls, ChannelVerdict.HEALTHY, "ok"),
                api(calls, ChannelVerdict.HEALTHY, "ok"),
                glove(calls, ChannelVerdict.HEALTHY, "ok"));
        assertThat(calls).containsExactly(ProbeChannel.ENDPOINT);
        assertThat(decision.disposition()).isEqualTo(ProbeDisposition.INCIDENT);
        assertThat(decision.symptomType()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test void fallbackHappensOnlyWhenTheChannelItselfFails() {
        var calls = new ArrayList<ProbeChannel>();
        var decision = new ProbeRunner().check(target(ProbeChannel.VM, ProbeChannel.ENDPOINT, ProbeChannel.API),
                endpoint(calls, ChannelVerdict.CHANNEL_FAILED, "timeout"),
                mcp(calls, ChannelVerdict.HEALTHY, "should not run"),
                api(calls, ChannelVerdict.HEALTHY, "200"),
                glove(calls, ChannelVerdict.HEALTHY, "should not run"));
        assertThat(calls).containsExactly(ProbeChannel.ENDPOINT, ProbeChannel.API);
        assertThat(decision.disposition()).isEqualTo(ProbeDisposition.CHANNEL_DEGRADED);
        assertThat(decision.dedupKey()).isEqualTo("channel-degraded:jenkins-prod:ENDPOINT");
    }

    @Test void everyConfiguredChannelIsTriedWhenEachChannelFails() {
        var calls = new ArrayList<ProbeChannel>();
        var decision = new ProbeRunner().check(target(ProbeChannel.API, ProbeChannel.VM),
                endpoint(calls, ChannelVerdict.HEALTHY, "should not run"),
                mcp(calls, ChannelVerdict.HEALTHY, "should not run"),
                api(calls, ChannelVerdict.CHANNEL_FAILED, "auth failure"),
                glove(calls, ChannelVerdict.CHANNEL_FAILED, "mcp server unavailable"));
        assertThat(calls).containsExactly(ProbeChannel.API, ProbeChannel.VM);
        assertThat(decision.disposition()).isEqualTo(ProbeDisposition.INCIDENT);
        assertThat(decision.symptomType()).isEqualTo("HEALTH_CHECK_FAILED");
        assertThat(decision.summary()).contains("auth failure", "mcp server unavailable");
    }

    private static ProbeTarget target(ProbeChannel... channels) {
        return new ProbeTarget("jenkins-prod", "jenkins", "prod", List.of(channels),
                "https://jenkins/login", "https://jenkins/api", "glove", "vm-jenkins-01");
    }

    private static EndpointProbePort endpoint(List<ProbeChannel> calls, ChannelVerdict verdict, String detail) {
        return target -> record(calls, ProbeChannel.ENDPOINT, verdict, detail);
    }

    private static McpProbePort mcp(List<ProbeChannel> calls, ChannelVerdict verdict, String detail) {
        return target -> record(calls, ProbeChannel.MCP, verdict, detail);
    }

    private static ApiProbePort api(List<ProbeChannel> calls, ChannelVerdict verdict, String detail) {
        return target -> record(calls, ProbeChannel.API, verdict, detail);
    }

    private static GloveProbePort glove(List<ProbeChannel> calls, ChannelVerdict verdict, String detail) {
        return target -> record(calls, ProbeChannel.VM, verdict, detail);
    }

    private static ChannelAttempt record(List<ProbeChannel> calls, ProbeChannel channel, ChannelVerdict verdict, String detail) {
        calls.add(channel);
        return new ChannelAttempt(channel, verdict, detail);
    }
}
