package com.vlink.backend.api;

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

// ApiExceptionHandlerTest unit-tests the handler methods directly, bypassing Spring's
// resolver-priority ordering entirely. These tests exercise the real DispatcherServlet pipeline,
// which is the only way to catch a regression where the generic Exception.class catch-all
// (Milestone 4) shadows Spring's own correct 400/404 handling for well-known MVC exceptions —
// exactly the bug found live against the docker-compose demo (see CLAUDE.md's ApiExceptionHandler note).
@SpringBootTest
@AutoConfigureMockMvc
class ApiExceptionHandlerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String registerPromoter() throws Exception {
        String email = "exhandler-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"PROMOTER\"}"
            .formatted(email);
        String response = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void malformedRequestBodyReturnsBadRequestNotAGenericServerError() throws Exception {
        String promoterToken = registerPromoter();
        // "type" com um valor que não existe no enum Event.Type — falha a desserialização Jackson
        // (HttpMessageNotReadableException), não uma violação de @Valid. Antes da correção, o
        // catch-all genérico intercetava isto e devolvia 500 em vez do 400 correto.
        String body = "{\"title\":\"x\",\"location\":\"y\",\"type\":\"NOT_A_REAL_TYPE\",\"capacity\":1,"
            + "\"startDate\":\"2027-01-01T10:00:00\",\"endDate\":\"2027-01-01T12:00:00\",\"status\":\"PUBLISHED\"}";

        mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingStaticResourceReturnsNotFoundNotAGenericServerError() throws Exception {
        // /uploads/** é público mas serve ficheiros reais do disco — um caminho sem ficheiro
        // correspondente dispara NoResourceFoundException. Antes da correção, o catch-all
        // genérico intercetava isto e devolvia 500 em vez do 404 correto.
        mockMvc.perform(get("/uploads/events/999999/does-not-exist.jpg"))
            .andExpect(status().isNotFound());
    }
}
