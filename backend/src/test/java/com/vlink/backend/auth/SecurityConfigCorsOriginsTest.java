package com.vlink.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// app.cors.allowed-origins used to be hardcoded to http://localhost:5173 in SecurityConfig — now
// configurable via property, needed so the docker-compose demo (frontend published on a different
// port) isn't silently blocked by CORS. This fixes it against a real value, not just the dev default.
@SpringBootTest(properties = "app.cors.allowed-origins=http://foo.test,http://bar.test")
class SecurityConfigCorsOriginsTest {

    @Autowired
    CorsConfigurationSource corsConfigurationSource;

    @Test
    void allowedOriginsPropertyIsHonoredInsteadOfTheHardcodedDevDefault() {
        CorsConfiguration config = corsConfigurationSource.getCorsConfiguration(new MockHttpServletRequest());

        assertTrue(config.getAllowedOrigins().contains("http://foo.test"));
        assertTrue(config.getAllowedOrigins().contains("http://bar.test"));
        assertFalse(config.getAllowedOrigins().contains("http://localhost:5173"));
    }
}
