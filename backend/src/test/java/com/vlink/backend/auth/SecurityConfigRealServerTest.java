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

    private String registerVolunteerAndGetToken() throws Exception {
        String email = "real-server-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"VOLUNTEER\"}"
            .formatted(email);
        RequestEntity<String> request = RequestEntity.post(URI.create("/auth/register"))
            .header(HttpHeaders.CONTENT_TYPE, "application/json")
            .body(body);
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    @Test
    void authenticatedRequestWithWrongRoleReturnsForbiddenNotUnauthorizedOnARealServer() throws Exception {
        String volunteerToken = registerVolunteerAndGetToken();

        RequestEntity<Void> request = RequestEntity.get(URI.create("/events/mine"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + volunteerToken)
            .build();
        ResponseEntity<String> response = restTemplate.exchange(request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
