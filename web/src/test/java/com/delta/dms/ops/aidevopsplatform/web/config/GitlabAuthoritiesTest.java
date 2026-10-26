package com.delta.dms.ops.aidevopsplatform.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabAuthoritiesTest {
    @Test void gitlabGroupsDoNotBecomeApplicationRoles() {
        var mapped = new GitlabAuthorities().mapAuthorities(List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("SCOPE_openid"),
                new SimpleGrantedAuthority("ROLE_USER")));
        assertThat(mapped).containsExactly(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
