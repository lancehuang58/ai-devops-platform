package com.delta.dms.ops.aidevopsplatform.model.monitoring;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelFailureTest {
    @Test void transportAuthAndMcpOutagesAreChannelFailures() throws Exception {
        assertThat(ChannelFailure.explain(new SocketTimeoutException("timed out"))).isEqualTo("timeout");
        assertThat(ChannelFailure.explain(new ConnectException("Connection refused"))).isEqualTo("connection refused");
        assertThat(ChannelFailure.explain(new UnknownHostException("jenkins.internal"))).isEqualTo("dns");
        assertThat(ChannelFailure.explain(new SSLHandshakeException("bad cert"))).isEqualTo("tls");
        assertThat(ChannelFailure.explain(new SecurityException("401 unauthorized"))).isEqualTo("auth failure");
        assertThat(ChannelFailure.explain(new IllegalStateException("mcp server unavailable"))).isEqualTo("mcp server unavailable");
    }
}
