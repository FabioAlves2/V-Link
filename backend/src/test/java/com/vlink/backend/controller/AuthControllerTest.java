package com.vlink.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlink.backend.auth.RegisterAttemptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RegisterAttemptService registerAttemptService;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private JsonNode register(String email, String password, String role) throws Exception {
        String body = "{\"name\":\"Test User\",\"email\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}"
            .formatted(email, password, role);
        String response = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    @Test
    void registerThenLoginSucceeds() throws Exception {
        String email = uniqueEmail("login-ok");
        register(email, "password123", "VOLUNTEER");

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        String email = uniqueEmail("short-pw");
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"123\"}".formatted(email)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        String email = uniqueEmail("bad-pw");
        register(email, "password123", "VOLUNTEER");

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"wrong\"}".formatted(email)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailedLoginsAreRateLimited() throws Exception {
        String email = uniqueEmail("rate-limited");
        register(email, "password123", "VOLUNTEER");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"wrong\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"wrong\"}".formatted(email)))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void repeatedDuplicateRegistrationAttemptsAreRateLimited() throws Exception {
        // O IP simulado pelo MockMvc é sempre o mesmo (127.0.0.1) — o limitador é partilhado com
        // o resto da suite (o contexto Spring é cacheado entre classes), por isso é reposto no
        // fim, para não bloquear registos legítimos de outros testes.
        String clientIp = "127.0.0.1";
        try {
            String email = uniqueEmail("dup-enum");
            register(email, "password123", "VOLUNTEER");

            String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email);
            for (int i = 0; i < 10; i++) {
                mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            }
            mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
        } finally {
            registerAttemptService.reset(clientIp);
        }
    }

    @Test
    void refreshTokenRotatesAndOldOneStopsWorking() throws Exception {
        String email = uniqueEmail("rotate");
        JsonNode tokens = register(email, "password123", "VOLUNTEER");
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refreshToken").exists());

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String email = uniqueEmail("logout");
        JsonNode tokens = register(email, "password123", "VOLUNTEER");
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
            .andExpect(status().isUnauthorized());
    }
}
