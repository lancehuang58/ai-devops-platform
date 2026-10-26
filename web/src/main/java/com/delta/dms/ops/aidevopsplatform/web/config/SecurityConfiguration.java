package com.delta.dms.ops.aidevopsplatform.web.config;

import com.delta.dms.ops.aidevopsplatform.service.port.out.DirectoryAuthenticationPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.RoleDirectory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import java.util.ArrayList;
import java.util.Arrays;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean AuthenticationManager authenticationManager(ObjectProvider<DirectoryAuthenticationPort> directories,
            ObjectProvider<RoleDirectory> roles) {
        return authentication -> {
            var directory=directories.getIfAvailable();
            if(directory==null) throw new BadCredentialsException("Authentication is not configured");
            var password=String.valueOf(authentication.getCredentials()).toCharArray();
            try {
                var user=directory.authenticate(authentication.getName(),password);
                var authorities=new ArrayList<org.springframework.security.core.GrantedAuthority>();
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                var roleDirectory=roles.getIfAvailable();
                if(roleDirectory!=null) roleDirectory.rolesFor(user).stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_"+role)).forEach(authorities::add);
                return UsernamePasswordAuthenticationToken.authenticated(user,null,authorities);
            } catch(SecurityException e) {throw new BadCredentialsException("Invalid credentials");}
            finally {Arrays.fill(password,'\0');}
        };
    }
    @Bean @Order(2) SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        return http.authenticationManager(authenticationManager)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // UI 路徑由優先序較高的管理頁面鏈把關；/error 讓 ERROR dispatch 直達容器錯誤頁。
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/","/login","/login-failure","/home","/logout","/css/**","/error").permitAll()
                        .requestMatchers("/api/v1/alerts/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
                .httpBasic(basic -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
