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

// Regressão (VULN-004 do security review): a consola H2 era acessível a qualquer conta com
// ROLE_PROMOTER — e qualquer visitante anónimo consegue auto-registar-se como PROMOTER
// (RegisterRequest.role é escolhido pelo próprio cliente, sem aprovação). Combinado com "dev"
// (o único perfil que liga a consola H2) ser o perfil ativo por defeito quando
// SPRING_PROFILES_ACTIVE não é definido num deploy real, isto dava a qualquer pessoa na
// internet uma consola SQL completa em duas chamadas HTTP. A consola H2 só serve para
// desenvolvimento local (sempre acedida via localhost), por isso restringir a loopback fecha o
// cenário de ataque remoto sem afetar o fluxo de desenvolvimento normal.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigH2ConsoleTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String registerPromoter() throws Exception {
        String email = "h2test-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"PROMOTER\"}"
            .formatted(email);
        String response = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    // MockMvc only dispatches through Spring MVC's DispatcherServlet — the real H2 console is a
    // separate raw Servlet (H2ConsoleAutoConfiguration) that MockMvc never actually invokes, so
    // even a fully-allowed request 404s here (no @Controller mapped to "/h2"). What this proves
    // is that Spring Security's filter chain let the request through to that point instead of
    // rejecting it with 401/403 — a live server test (SecurityConfigRealServerTest) exercises
    // the actual redirect for the loopback case.
    @Test
    void h2ConsoleRequestIsNotBlockedByAuthorizationForAPromoterConnectingFromLoopback() throws Exception {
        String promoterToken = registerPromoter();

        mockMvc.perform(get("/h2").header("Authorization", "Bearer " + promoterToken)
                .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; }))
            .andExpect(status().isNotFound());
    }

    @Test
    void h2ConsoleIsBlockedForAPromoterConnectingFromANonLoopbackAddress() throws Exception {
        String promoterToken = registerPromoter();

        mockMvc.perform(get("/h2").header("Authorization", "Bearer " + promoterToken)
                .with(req -> { req.setRemoteAddr("203.0.113.5"); return req; }))
            .andExpect(status().isForbidden());
    }

    @Test
    void h2ConsoleIsBlockedForAVolunteerEvenFromLoopback() throws Exception {
        String email = "h2test-vol-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"VOLUNTEER\"}"
            .formatted(email);
        String response = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String volunteerToken = objectMapper.readTree(response).get("token").asText();

        mockMvc.perform(get("/h2").header("Authorization", "Bearer " + volunteerToken)
                .with(req -> { req.setRemoteAddr("127.0.0.1"); return req; }))
            .andExpect(status().isForbidden());
    }
}
