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
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/tenants/register").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // API key protected endpoints
                        .requestMatchers("/api/v1/items/**").authenticated()
                        .requestMatchers("/api/v1/interactions/**").authenticated()
                        .requestMatchers("/api/v1/recommendations/**").authenticated()
                        // JWT protected endpoints
                        .requestMatchers("/api/v1/dashboard/**").authenticated()
                        .requestMatchers("/api/v1/auth/refresh").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
