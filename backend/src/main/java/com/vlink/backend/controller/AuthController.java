package com.vlink.backend.controller;

import com.vlink.backend.auth.JwtUtil;
import com.vlink.backend.auth.LoginAttemptService;
import com.vlink.backend.dto.*;
import com.vlink.backend.model.RefreshToken;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.RefreshTokenRepository;
import com.vlink.backend.repo.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptService loginAttemptService;

    private void persistRefreshToken(String email, String refreshToken) {
        Claims claims = jwtUtil.extractClaims(refreshToken);
        RefreshToken rt = new RefreshToken();
        rt.setJti(claims.getId());
        rt.setUserEmail(email);
        rt.setExpiresAt(claims.getExpiration().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        refreshTokenRepository.save(rt);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.email()))
            return ResponseEntity.badRequest().body("Este email já está registado.");

        User user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(req.role() != null ? req.role() : User.Role.VOLUNTEER);
        userRepository.save(user);

        String access = jwtUtil.generateToken(user.getEmail(), user.getRole());
        String refresh = jwtUtil.generateRefreshToken(user.getEmail(), user.getRole());
        persistRefreshToken(user.getEmail(), refresh);

        return ResponseEntity.ok(Map.of("token", access, "refreshToken", refresh));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        if (loginAttemptService.isBlocked(req.email())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Demasiadas tentativas. Tenta novamente mais tarde."));
        }

        return userRepository.findByEmail(req.email())
            .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
            .map(u -> {
                loginAttemptService.reset(req.email());
                String access = jwtUtil.generateToken(u.getEmail(), u.getRole());
                String refresh = jwtUtil.generateRefreshToken(u.getEmail(), u.getRole());
                persistRefreshToken(u.getEmail(), refresh);
                return ResponseEntity.ok(Map.of("token", access, "refreshToken", refresh));
            })
            .orElseGet(() -> {
                loginAttemptService.recordFailure(req.email());
                return ResponseEntity.status(401).body(Map.of("error", "Credenciais inválidas."));
            });
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req) {
        String refreshToken = req.refreshToken();
        if (!jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken))
            return ResponseEntity.status(401).body(Map.of("error", "Sessão expirada. Faz login novamente."));

        RefreshToken stored = refreshTokenRepository.findByJti(jwtUtil.extractJti(refreshToken)).orElse(null);
        if (stored == null || stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now()))
            return ResponseEntity.status(401).body(Map.of("error", "Sessão expirada. Faz login novamente."));

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String email = jwtUtil.extractEmail(refreshToken);
        User.Role role = User.Role.valueOf(jwtUtil.extractRole(refreshToken));

        String newAccess = jwtUtil.generateToken(email, role);
        String newRefresh = jwtUtil.generateRefreshToken(email, role);
        persistRefreshToken(email, newRefresh);

        return ResponseEntity.ok(Map.of("token", newAccess, "refreshToken", newRefresh));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody RefreshRequest req) {
        try {
            refreshTokenRepository.findByJti(jwtUtil.extractJti(req.refreshToken()))
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
        } catch (Exception ignored) {
            // token já inválido/expirado — não há nada para revogar; logout é sempre bem-sucedido do lado do cliente
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
            .map(u -> ResponseEntity.ok(Map.of(
                "name",  u.getName(),
                "email", u.getEmail(),
                "role",  u.getRole()
            )))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(Authentication auth,
                                       @Valid @RequestBody UpdateProfileRequest req) {
        return userRepository.findByEmail(auth.getName())
            .map(u -> {
                if (req.name() != null && !req.name().isBlank())
                    u.setName(req.name());
                if (req.password() != null && !req.password().isBlank()) {
                    if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), u.getPassword())) {
                        return ResponseEntity.status(401).body(Map.of("error", "Password atual incorreta."));
                    }
                    u.setPassword(passwordEncoder.encode(req.password()));
                }
                userRepository.save(u);
                return ResponseEntity.ok(Map.of(
                    "name",  u.getName(),
                    "email", u.getEmail(),
                    "role",  u.getRole()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
