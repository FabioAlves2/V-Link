package com.vlink.backend.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

// Rate limiting simples em memória para /auth/login — suficiente para uma instância única,
// sem necessidade de Redis. Reinicia (perde o histórico) sempre que o backend reinicia.
@Component
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 15 * 60;

    private record Attempt(int count, Instant windowStart) {}

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String email) {
        Attempt a = attempts.get(key(email));
        if (a == null) return false;
        if (windowExpired(a)) {
            attempts.remove(key(email));
            return false;
        }
        return a.count() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String email) {
        attempts.compute(key(email), (k, a) -> {
            if (a == null || windowExpired(a)) {
                return new Attempt(1, Instant.now());
            }
            return new Attempt(a.count() + 1, a.windowStart());
        });
    }

    public void reset(String email) {
        attempts.remove(key(email));
    }

    private boolean windowExpired(Attempt a) {
        return Instant.now().isAfter(a.windowStart().plusSeconds(WINDOW_SECONDS));
    }

    private String key(String email) {
        return email == null ? "" : email.toLowerCase();
    }
}
