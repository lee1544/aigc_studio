package com.example.aigc.config;

import com.example.aigc.exception.ApiResponse;
import com.example.aigc.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .cors().and()
                .headers()
                .cacheControl().disable()
                .addHeaderWriter((request, response) -> {
                    String uri = request.getRequestURI();
                    if (uri != null && uri.startsWith("/admin/assets/")) {
                        response.setHeader("Cache-Control", "public, max-age=300");
                        return;
                    }
                    if (uri != null && (uri.equals("/admin") || uri.startsWith("/admin/"))) {
                        response.setHeader("Cache-Control", "no-cache");
                        return;
                    }
                    response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                })
                .and()
                .authorizeRequests()
                .antMatchers("/admin", "/admin/**", "/favicon.ico").permitAll()
                .antMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
                .and()
                // Spring Security 自身的异常不走 GlobalExceptionHandler，这里统一返回 JSON 响应体
                .exceptionHandling()
                .authenticationEntryPoint((request, response, ex) ->
                        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录状态已失效"))
                .accessDeniedHandler((request, response, ex) ->
                        writeJson(response, HttpServletResponse.SC_FORBIDDEN, "没有权限访问"))
                .and()
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiResponse<>(status, message)));
    }
}
