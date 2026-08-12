package com.vlink.backend.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void favoritesConstraintViolationIsClassifiedAsFavoriteConflictNotSubscriptionConflict() {
        // Mensagem real do H2 para uk_favorites_user_event contém tanto "favorite" (nome da
        // tabela/constraint) como user_id/event_id (nomes das colunas) — sem o branch de
        // favorites vir primeiro, isto cairia no ramo de SUBSCRIPTION_CONFLICT por engano.
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "could not execute statement; SQL [n/a]; constraint [UK_FAVORITES_USER_EVENT_USER_ID_EVENT_ID]");

        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("FAVORITE_CONFLICT", response.getBody().get("code"));
    }

    @Test
    void subscriptionsConstraintViolationIsStillClassifiedAsSubscriptionConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "could not execute statement; SQL [n/a]; constraint [UK_SUBSCRIPTIONS_USER_ID_EVENT_ID]");

        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("SUBSCRIPTION_CONFLICT", response.getBody().get("code"));
    }

    @Test
    void emailConstraintViolationIsStillClassifiedAsUserEmailConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "could not execute statement; SQL [n/a]; constraint [UK_USERS_EMAIL]");

        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("USER_EMAIL_CONFLICT", response.getBody().get("code"));
    }

    @Test
    void unrecognizedConstraintViolationFallsBackToGenericDataConflict() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "could not execute statement; SQL [n/a]; constraint [SOME_OTHER_CONSTRAINT]");

        ResponseEntity<Map<String, String>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DATA_CONFLICT", response.getBody().get("code"));
    }
}
