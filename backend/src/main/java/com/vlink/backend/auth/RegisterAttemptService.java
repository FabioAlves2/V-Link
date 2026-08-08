package com.vlink.backend.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

// Limita, por IP, quantas respostas "email já registado" um cliente pode receber numa janela —
// sem isto, /auth/register permite enumerar contas existentes testando muitos emails em sequência.
// Só conta tentativas com email duplicado, nunca registos bem-sucedidos, para não interferir com
// uso normal (nem com os testes, que registam dezenas de contas novas por classe).
@Component
public class RegisterAttemptService {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 15 * 60;

    private record Attempt(int count, Instant windowStart) {}

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        Attempt a = attempts.get(key(ip));
        if (a == null) return false;
        if (windowExpired(a)) {
            attempts.remove(key(ip));
            return false;
        }
        return a.count() >= MAX_ATTEMPTS;
    }

    public void recordDuplicateAttempt(String ip) {
        attempts.compute(key(ip), (k, a) -> {
            if (a == null || windowExpired(a)) {
                return new Attempt(1, Instant.now());
            }
            return new Attempt(a.count() + 1, a.windowStart());
        });
    }

    public void reset(String ip) {
        attempts.remove(key(ip));
    }

    private boolean windowExpired(Attempt a) {
        return Instant.now().isAfter(a.windowStart().plusSeconds(WINDOW_SECONDS));
    }

    private String key(String ip) {
        return ip == null ? "" : ip;
    }
}
