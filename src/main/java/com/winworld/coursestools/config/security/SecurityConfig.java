package com.winworld.coursestools.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static com.winworld.coursestools.config.security.PublicUrlsHolder.PUBLIC_URL_PATTERNS;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@EnableWebSecurity
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${cors.domains.web}")
    private String webDomain;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtRequestFilter jwtRequestFilter
    ) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfiguration = new CorsConfiguration();
                    corsConfiguration.setAllowedOriginPatterns(List.of(
                            "https://*." + webDomain,
                            "http://localhost:*"
                    ));
                    corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                    corsConfiguration.setAllowedHeaders(List.of("*"));
                    corsConfiguration.setAllowCredentials(true);
                    return corsConfiguration;
                }))
                .authorizeHttpRequests(request -> request
                        .requestMatchers(PUBLIC_URL_PATTERNS).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(manager -> manager.sessionCreationPolicy(STATELESS));
        return http.build();
    }
}
