package com.vlink.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// MockMvc não reproduz este comportamento: quando o AccessDeniedHandler chama
// response.sendError(403), um Tomcat real reencaminha internamente para /error para gerar o
// corpo do erro, e esse pedido reencaminhado volta a passar pela cadeia de filtros do Spring
// Security. Sem "/error" na lista de permitAll, essa segunda passagem (já sem a autenticação
// original) acabava a substituir o 403 por um 401 antes de chegar ao cliente — só visível
// contra um servidor real, daí RANDOM_PORT + TestRestTemplate em vez de MockMvc.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigRealServerTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired ObjectMapper objectMapper;

    private String registerAndGetToken(String role) throws Exception {
        String email = "real-server-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}"
            .formatted(email, role);
        RequestEntity<String> request = RequestEntity.post(URI.create("/auth/register"))
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(body);
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    @Test
    void authenticatedRequestWithWrongRoleReturnsForbiddenNotUnauthorizedOnARealServer() throws Exception {
        String volunteerToken = registerAndGetToken("VOLUNTEER");

        RequestEntity<Void> request = RequestEntity.get(URI.create("/events/mine"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + volunteerToken)
            .build();
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // Regressão (VULN-004 do security review): a consola H2 é gerida por um Servlet real,
    // separado do DispatcherServlet — só um servidor a sério (não MockMvc) invoca o
    // redireccionamento genuíno para a página de login da consola. TestRestTemplate liga sempre
    // via localhost e segue redireccionamentos automaticamente, por isso um 200 final (a própria
    // página de login da consola) confirma que o acesso local legítimo continua a funcionar; o
    // caminho "bloqueado a partir de fora" está coberto em SecurityConfigH2ConsoleTest (via
    // MockMvc, que permite simular um remoteAddr não-loopback).
    @Test
    void h2ConsoleIsReachableForAPromoterOnARealLoopbackConnection() throws Exception {
        String promoterToken = registerAndGetToken("PROMOTER");

        RequestEntity<Void> request = RequestEntity.get(URI.create("/h2"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + promoterToken)
            .build();
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
