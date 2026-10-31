package com.delta.dms.ops.aidevopsplatform.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;

/** Formal UI chain. GitLab is the only identity provider. It is not loaded on the default or demo profile. */
@Configuration(proxyBeanMethods = false)
@Profile("platform")
public class PlatformWebSecurityConfiguration {
    static final String[] UI_PATHS = {"/", "/login", "/login-failure", "/home", "/work-items", "/work-items/**",
            "/chat", "/chat/**", "/logout", "/css/**", "/vendor/**", "/oauth2/**", "/login/oauth2/**"};
    private static final RequestMatcher UI_POSTS = request -> Arrays.stream(UI_PATHS)
            .anyMatch(pattern -> PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, pattern).matches(request));

    @Bean
    @Order(1)
    SecurityFilterChain platformUiFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher(UI_PATHS)
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(UI_POSTS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/login-failure", "/css/**", "/vendor/**", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth.loginPage("/login")
                        .defaultSuccessUrl("/work-items", false)
                        .failureUrl("/login-failure")
                        .userInfoEndpoint(user -> user.userAuthoritiesMapper(new GitlabAuthorities())))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .build();
    }
}
