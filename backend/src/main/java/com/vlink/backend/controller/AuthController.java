package com.vlink.backend.controller;

import com.vlink.backend.auth.JwtUtil;
import com.vlink.backend.dto.LoginRequest;
import com.vlink.backend.dto.RefreshRequest;
import com.vlink.backend.dto.RegisterRequest;
import com.vlink.backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // Temporário em memória — substituir por UserRepository quando tiveres BD
    private final Map<String, User> users = new ConcurrentHashMap<>();

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (users.containsKey(req.email()))
            return ResponseEntity.badRequest().body("Email já registado");

        User user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(req.role() != null ? req.role() : User.Role.VOLUNTEER);

        users.put(user.getEmail(), user);

        return ResponseEntity.ok(Map.of(
            "token",        jwtUtil.generateToken(user.getEmail(), user.getRole()),
            "refreshToken", jwtUtil.generateRefreshToken(user.getEmail(), user.getRole())
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        User user = users.get(req.email());

        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword()))
            return ResponseEntity.status(401).body("Credenciais inválidas");

        return ResponseEntity.ok(Map.of(
            "token",        jwtUtil.generateToken(user.getEmail(), user.getRole()),
            "refreshToken", jwtUtil.generateRefreshToken(user.getEmail(), user.getRole())
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        if (!jwtUtil.isTokenValid(req.refreshToken()))
            return ResponseEntity.status(401).body("Refresh token inválido ou expirado");

        String email = jwtUtil.extractEmail(req.refreshToken());
        String role  = jwtUtil.extractRole(req.refreshToken());

        return ResponseEntity.ok(Map.of(
            "token",        jwtUtil.generateToken(email, User.Role.valueOf(role)),
            "refreshToken", jwtUtil.generateRefreshToken(email, User.Role.valueOf(role))
        ));
    }
}