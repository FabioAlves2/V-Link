package com.vlink.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sem um AuthenticationEntryPoint explícito, o Spring Security usa por defeito o
// Http403ForbiddenEntryPoint quando não há httpBasic()/formLogin() configurado — devolvendo 403
// (não 401) para qualquer pedido sem token válido. Isso desativava por completo o refresh
// silencioso do axiosConfig.js (só reage a 401), deixando qualquer sessão mais longa que os 15 min
// do access token presa em 403s permanentes. Estes testes fixam o comportamento correto.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigAuthEntryPointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String registerAndGetToken() throws Exception {
        String email = "entrypoint-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"VOLUNTEER\"}"
            .formatted(email);
        String response = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void requestWithNoTokenAtAllReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/subscriptions"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithMalformedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/subscriptions").header("Authorization", "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithARefreshTokenInsteadOfAnAccessTokenReturnsUnauthorized() throws Exception {
        // JwtAuthFilter só aceita type=access; um refresh token usado aqui é tratado como
        // "sem autenticação válida", não como "autenticado mas sem role" — deve dar 401.
        String email = "entrypoint-refresh-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"VOLUNTEER\"}"
            .formatted(email);
        String response = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(response).get("refreshToken").asText();

        mockMvc.perform(get("/subscriptions").header("Authorization", "Bearer " + refreshToken))
            .andExpect(status().isUnauthorized());
    }

    // Regressão: um utilizador autenticado (token de acesso válido) sem a role certa continua a
    // dar 403 — esse caso passa pelo AccessDeniedHandler, não pelo AuthenticationEntryPoint, e não
    // deve ser afetado por esta correção.
    @Test
    void authenticatedRequestWithWrongRoleStillReturnsForbidden() throws Exception {
        String volunteerToken = registerAndGetToken();
        mockMvc.perform(get("/events/mine").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isForbidden());
    }
}
