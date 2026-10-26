package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Fixed probe order. A later channel runs only after the current channel itself fails. */
public final class ProbeChain {
    public static final List<ProbeChannel> PRIORITY = List.of(
            ProbeChannel.ENDPOINT, ProbeChannel.MCP, ProbeChannel.API, ProbeChannel.VM);

    private ProbeChain() {}

    public static List<ProbeChannel> order(List<ProbeChannel> configured) {
        var wanted = new LinkedHashSet<>(configured);
        return PRIORITY.stream().filter(wanted::contains).toList();
    }

    public static ProbeDecision decide(String instanceId, List<ChannelAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            throw new IllegalArgumentException("no probe channel configured");
        }
        var considered = new ArrayList<ChannelAttempt>();
        ChannelAttempt answer = null;
        for (var attempt : attempts) {
            considered.add(attempt);
            if (attempt.verdict() != ChannelVerdict.CHANNEL_FAILED) {
                answer = attempt;
                break;
            }
        }
        var trace = List.copyOf(considered);
        if (answer == null) {
            return new ProbeDecision(ProbeDisposition.INCIDENT, "HEALTH_CHECK_FAILED", trace(trace), null, trace, null);
        }
        if (answer.verdict() == ChannelVerdict.UNHEALTHY) {
            var summary = new StringBuilder();
            for (var attempt : trace) {
                if (attempt.verdict() == ChannelVerdict.CHANNEL_FAILED) {
                    summary.append(line(attempt)).append("; ");
                }
            }
            summary.append(answer.channel()).append(" reported the service unhealthy: ").append(answer.detail());
            return new ProbeDecision(ProbeDisposition.INCIDENT, "SERVICE_UNAVAILABLE", summary.toString(), null, trace, answer.channel());
        }
        var failed = trace.stream().filter(attempt -> attempt.verdict() == ChannelVerdict.CHANNEL_FAILED).toList();
        if (failed.isEmpty()) {
            return new ProbeDecision(ProbeDisposition.HEALTHY, null, answer.channel() + " healthy: " + answer.detail(),
                    null, trace, answer.channel());
        }
        var key = "channel-degraded:" + instanceId + ":" + failed.stream()
                .map(attempt -> attempt.channel().name()).reduce((left, right) -> left + "+" + right).orElse("");
        var summary = trace(failed) + "; " + answer.channel() + " shows the service healthy";
        return new ProbeDecision(ProbeDisposition.CHANNEL_DEGRADED, null, summary, key, trace, answer.channel());
    }

    private static String trace(List<ChannelAttempt> attempts) {
        return attempts.stream().map(ProbeChain::line).reduce((left, right) -> left + "; " + right).orElse("");
    }

    private static String line(ChannelAttempt attempt) {
        return attempt.channel() + " channel failed: " + attempt.detail();
    }
}
