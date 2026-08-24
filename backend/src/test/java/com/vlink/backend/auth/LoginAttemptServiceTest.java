package com.vlink.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private static final String IP = "10.0.0.1";
    private static final String OTHER_IP = "10.0.0.2";

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void notBlockedBeforeAnyFailures() {
        assertThat(service.isBlocked("user@example.com", IP)).isFalse();
    }

    @Test
    void blockedAfterFiveFailures() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("user@example.com", IP);
        }
        assertThat(service.isBlocked("user@example.com", IP)).isTrue();
    }

    @Test
    void notBlockedAfterOnlyFourFailures() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("user@example.com", IP);
        }
        assertThat(service.isBlocked("user@example.com", IP)).isFalse();
    }

    @Test
    void resetClearsFailureCount() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("user@example.com", IP);
        }
        service.reset("user@example.com", IP);
        assertThat(service.isBlocked("user@example.com", IP)).isFalse();
    }

    @Test
    void trackingIsCaseInsensitiveAndPerEmail() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("User@Example.com", IP);
        }
        assertThat(service.isBlocked("user@example.com", IP)).isTrue();
        assertThat(service.isBlocked("someone-else@example.com", IP)).isFalse();
    }

    // Regressão: sem o IP na chave, um atacante que nunca soube a password podia bloquear o
    // login da vítima durante 15 min só com 5 tentativas erradas a partir de qualquer origem.
    @Test
    void failuresFromOneIpDoNotBlockTheVictimLoggingInFromAnotherIp() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("victim@example.com", IP);
        }
        assertThat(service.isBlocked("victim@example.com", IP)).isTrue();
        assertThat(service.isBlocked("victim@example.com", OTHER_IP)).isFalse();
    }
}
