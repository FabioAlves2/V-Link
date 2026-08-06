package com.vlink.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void notBlockedBeforeAnyFailures() {
        assertThat(service.isBlocked("user@example.com")).isFalse();
    }

    @Test
    void blockedAfterFiveFailures() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("user@example.com");
        }
        assertThat(service.isBlocked("user@example.com")).isTrue();
    }

    @Test
    void notBlockedAfterOnlyFourFailures() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("user@example.com");
        }
        assertThat(service.isBlocked("user@example.com")).isFalse();
    }

    @Test
    void resetClearsFailureCount() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("user@example.com");
        }
        service.reset("user@example.com");
        assertThat(service.isBlocked("user@example.com")).isFalse();
    }

    @Test
    void trackingIsCaseInsensitiveAndPerEmail() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("User@Example.com");
        }
        assertThat(service.isBlocked("user@example.com")).isTrue();
        assertThat(service.isBlocked("someone-else@example.com")).isFalse();
    }
}
