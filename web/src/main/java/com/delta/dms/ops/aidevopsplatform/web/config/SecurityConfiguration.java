package com.delta.dms.ops.aidevopsplatform.web.config;

import com.delta.dms.ops.aidevopsplatform.service.port.out.DirectoryAuthenticationPort;
import com.delta.dms.ops.aidevopsplatform.service.port.out.RoleDirectory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import java.util.Arrays;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean AuthenticationManager authenticationManager(ObjectProvider<DirectoryAuthenticationPort> directories,
            ObjectProvider<RoleDirectory> roles) {
        return authentication -> {
            var directory=directories.getIfAvailable(); var roleDirectory=roles.getIfAvailable();
            if(directory==null || roleDirectory==null) throw new BadCredentialsException("Authentication is not configured");
            var password=String.valueOf(authentication.getCredentials()).toCharArray();
            try {
                var user=directory.authenticate(authentication.getName(),password);
                var assigned=roleDirectory.rolesFor(user);
                if(assigned.isEmpty()) throw new BadCredentialsException("Access denied");
                return UsernamePasswordAuthenticationToken.authenticated(user,null,
                        assigned.stream().map(r -> new SimpleGrantedAuthority("ROLE_"+r)).toList());
            } catch(SecurityException e) {throw new BadCredentialsException("Invalid credentials");}
            finally {Arrays.fill(password,'\0');}
        };
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        return http.authenticationManager(authenticationManager)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
                .httpBasic(basic -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
