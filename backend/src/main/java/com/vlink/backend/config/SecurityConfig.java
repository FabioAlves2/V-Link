package com.vlink.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sem isto, um pedido sem token (ou com um token inválido/expirado) recebe 403 do
            // Http403ForbiddenEntryPoint por defeito do Spring Security (o fallback quando não há
            // httpBasic()/formLogin() configurado) — não 401. Isso desativava por completo o
            // refresh silencioso do axiosConfig.js (só reage a 401), deixando qualquer sessão mais
            // longa que os 15 min do access token presa em 403s permanentes até um logout manual.
            // 403 continua correto (e intocado) para um utilizador autenticado sem a role certa
            // — esse caso passa pelo AccessDeniedHandler, não pelo AuthenticationEntryPoint.
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/login", "/auth/register", "/auth/refresh", "/auth/logout").permitAll()
                // Um AccessDeniedException (papel errado) chama response.sendError(403), o que faz
                // o Tomcat reencaminhar internamente para /error para gerar o corpo do erro — e
                // esse pedido reencaminhado volta a passar por esta cadeia de filtros. Sem isto,
                // "/error" cai no anyRequest().authenticated() genérico; como o reencaminhamento
                // já não tem a autenticação original (fica anónimo), essa segunda verificação nega
                // outra vez o acesso — mas agora como não-autenticado, e o AuthenticationEntryPoint
                // (401) sobrepõe-se ao 403 original antes de chegar ao cliente. Só visível num
                // servidor real (Tomcat) — o MockMvc não reproduz o reencaminhamento /error, por
                // isso os testes existentes (incl. o que fixa este 403) nunca apanharam isto.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/events/mine", "/events/*/subscribers").hasRole("PROMOTER")
                .requestMatchers(HttpMethod.GET, "/events", "/events/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/events", "/events/**").hasRole("PROMOTER")
                .requestMatchers(HttpMethod.PUT, "/events/**").hasRole("PROMOTER")
                .requestMatchers(HttpMethod.DELETE, "/events/**").hasRole("PROMOTER")
                .requestMatchers("/auth/me", "/subscriptions/**", "/notifications/**", "/favorites/**").authenticated()
                // Papel sozinho não chega aqui: qualquer conta pode auto-registar-se como PROMOTER
                // (RegisterRequest.role é escolhido pelo próprio cliente, sem aprovação), e o perfil
                // "dev" (o único que liga a consola H2) é o perfil ativo por defeito se
                // SPRING_PROFILES_ACTIVE nunca for definido num deploy real — um esquecimento
                // operacional, não um bug de código, mas que combinado com o auto-registo dava a
                // qualquer visitante anónimo da internet uma consola SQL completa sobre a base de
                // dados em duas chamadas HTTP. Restringir a loopback neutraliza esse cenário por
                // completo sem tocar no fluxo de desenvolvimento local (que já só acede via
                // localhost) nem no perfil por defeito.
                .requestMatchers("/h2/**", "/h2-console/**").access(
                    new WebExpressionAuthorizationManager("hasRole('PROMOTER') and (hasIpAddress('127.0.0.1') or hasIpAddress('::1'))"))
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}