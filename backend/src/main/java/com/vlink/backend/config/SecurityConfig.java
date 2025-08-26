package com.vlink.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .cors(cors -> {}) // afinamos mais tarde
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/**").permitAll() // abrir API em dev
        .anyRequest().permitAll()
      );
    return http.build();
  }
}