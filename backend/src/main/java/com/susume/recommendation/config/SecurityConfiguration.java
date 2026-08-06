package com.susume.recommendation.config;

import com.susume.recommendation.filter.ApiKeyFilter;
import com.susume.recommendation.filter.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final ApiKeyFilter apiKeyFilter;
    private final JwtFilter jwtFilter;

    public SecurityConfiguration(ApiKeyFilter apiKeyFilter, JwtFilter jwtFilter) {
        this.apiKeyFilter = apiKeyFilter;
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/register-admin").permitAll()
                        .requestMatchers("/api/v1/auth/register").permitAll()
                        .requestMatchers("/api/v1/auth/verify-email").permitAll()
                        .requestMatchers("/api/v1/auth/resend-verification").permitAll()
                        .requestMatchers("/api/v1/auth/forgot-password").permitAll()
                        .requestMatchers("/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/api/v1/invitations/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // API key protected endpoints
                        .requestMatchers("/api/v1/items/**").authenticated()
                        .requestMatchers("/api/v1/interactions/**").authenticated()
                        .requestMatchers("/api/v1/recommendations/**").authenticated()
                        // JWT protected endpoints
                        .requestMatchers("/api/v1/dashboard/**").authenticated()
                        .requestMatchers("/api/v1/auth/refresh").authenticated()
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/logout-all").authenticated()
                        .requestMatchers("/api/v1/api-keys/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.Arrays.asList("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(
                java.util.Arrays.asList("Authorization", "Content-Type", "X-API-KEY", "X-TENANT-SLUG"));
        configuration.setExposedHeaders(java.util.Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
