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

    // Chave = email + IP, não só o email: só por email, 5 pedidos com password errada de
    // *qualquer* origem bloqueavam a vítima durante 15 min mesmo com a password certa —
    // um DoS de conta trivial de disparar por um atacante que nunca soube a password.
    // Com o IP na chave, o login legítimo da vítima (a partir da rede dela) usa uma chave
    // diferente da do atacante e não é afetado.
    public boolean isBlocked(String email, String ip) {
        Attempt a = attempts.get(key(email, ip));
        if (a == null) return false;
        if (windowExpired(a)) {
            attempts.remove(key(email, ip));
            return false;
        }
        return a.count() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String email, String ip) {
        attempts.compute(key(email, ip), (k, a) -> {
            if (a == null || windowExpired(a)) {
                return new Attempt(1, Instant.now());
            }
            return new Attempt(a.count() + 1, a.windowStart());
        });
    }

    public void reset(String email, String ip) {
        attempts.remove(key(email, ip));
    }

    private boolean windowExpired(Attempt a) {
        return Instant.now().isAfter(a.windowStart().plusSeconds(WINDOW_SECONDS));
    }

    // trim() + toLowerCase(): sem o trim, "email@x.com" e " email@x.com" geravam chaves
    // diferentes (nunca coincidindo com a conta real em AuthController.login, que também não
    // faz trim — por isso inofensivo na prática, mas inconsistente) — normaliza aqui da mesma
    // forma que faria sentido em qualquer comparação de email.
    private String key(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
    }
}
