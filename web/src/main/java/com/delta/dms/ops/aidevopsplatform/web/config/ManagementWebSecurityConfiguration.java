package com.delta.dms.ops.aidevopsplatform.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpMethod;
import java.util.Arrays;

/**
 * demo profile 專屬的管理頁面安全鏈：表單登入 + HTTP Session + CSRF。
 * 只匹配明確的 UI 路徑，其餘請求維持原有 HTTP Basic／STATELESS 鏈。
 */
@Configuration(proxyBeanMethods=false)
@Profile("demo")
public class ManagementWebSecurityConfiguration {
    static final String[] UI_PATHS={"/","/login","/login-failure","/home","/logout","/css/**"};
    // CSRF 失敗必須停在 UI 鏈並回 403，不能讓請求落到 API 鏈變成 Basic 401。
    private static final RequestMatcher UI_POSTS = request -> Arrays.stream(UI_PATHS)
            .anyMatch(pattern -> PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, pattern).matches(request));

    @Bean @Order(1) SecurityFilterChain managementUiFilterChain(HttpSecurity http,
            AuthenticationManager authenticationManager) throws Exception {
        return http.securityMatcher(UI_PATHS).authenticationManager(authenticationManager)
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(UI_POSTS))
                .authorizeHttpRequests(auth -> auth
                        // 靜態資源與登入頁公開；其餘 UI 路徑（含 POST /、GET /）一律先登入。
                        .requestMatchers("/login","/login-failure","/css/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                        .defaultSuccessUrl("/home",true).failureUrl("/login-failure").permitAll())
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .build();
    }
}
