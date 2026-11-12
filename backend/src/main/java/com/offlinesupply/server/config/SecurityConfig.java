package com.offlinesupply.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offlinesupply.server.security.SessionAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SessionAuthFilter sessionAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(SessionAuthFilter sessionAuthFilter, ObjectMapper objectMapper) {
        this.sessionAuthFilter = sessionAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Session cookie is SameSite=Strict and every request also requires the
                // enrolled device headers, so the classic CSRF vector does not apply;
                // documented in the companion document.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/actuator/health").permitAll()
                        // Static SPA assets and client-side routes are public; the app
                        // itself still requires a session for every API call.
                        .requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/assets/**", "/fonts/**", "/favicon.ico", "/vite.svg",
                                "/login", "/items/**", "/record", "/sync", "/discrepancies", "/discrepancies/**", "/issuer")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/issuer/designate").hasAuthority("SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/discrepancies/*/resolve").hasAuthority("SUPERVISOR")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, 401, "UNAUTHORIZED", "authentication required"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, 403, "FORBIDDEN", "this action requires a supervisor session")));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength 12 per the Prompt 4 rules.
        return new BCryptPasswordEncoder(12);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", code);
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
