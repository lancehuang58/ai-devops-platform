package com.delta.dms.ops.aidevopsplatform.web.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;

import java.util.Collection;
import java.util.List;

/** GitLab groups are not application roles. Every authenticated user is ROLE_USER. */
public final class GitlabAuthorities implements GrantedAuthoritiesMapper {
    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
