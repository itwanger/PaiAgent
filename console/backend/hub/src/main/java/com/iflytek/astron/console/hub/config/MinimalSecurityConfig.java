package com.iflytek.astron.console.hub.config;

import com.iflytek.astron.console.commons.config.JwtClaimsFilter;
import com.iflytek.astron.console.commons.entity.user.UserInfo;
import com.iflytek.astron.console.commons.enums.space.EnterpriseServiceTypeEnum;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Minimal profile security configuration - disable all authentication
 * This config replaces SecurityConfig when profile=minimal
 */
@Configuration
@EnableWebSecurity
@Profile("minimal")
public class MinimalSecurityConfig {

    @Bean
    public SecurityFilterChain minimalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()  // Allow all requests without authentication
            )
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .securityContext(AbstractHttpConfigurer::disable)
            .sessionManagement(AbstractHttpConfigurer::disable)
            .addFilterBefore(mockUserFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public OncePerRequestFilter mockUserFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
                    throws ServletException, IOException {
                // Mock user info for local development
                String mockUid = "local-dev-uid";
                request.setAttribute(JwtClaimsFilter.USER_ID_ATTRIBUTE, mockUid);
                
                UserInfo mockUser = new UserInfo();
                mockUser.setUid(mockUid);
                mockUser.setUsername("本地开发用户");
                mockUser.setAvatar("");
                mockUser.setMobile("");
                mockUser.setAccountStatus(1);
                mockUser.setEnterpriseServiceType(EnterpriseServiceTypeEnum.NONE);
                mockUser.setUserAgreement(0);
                mockUser.setCreateTime(LocalDateTime.now());
                mockUser.setUpdateTime(LocalDateTime.now());
                mockUser.setDeleted(0);
                
                request.setAttribute(JwtClaimsFilter.USER_INFO_ATTRIBUTE, mockUser);
                
                filterChain.doFilter(request, response);
            }
        };
    }
}
