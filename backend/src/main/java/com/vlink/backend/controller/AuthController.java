package com.vlink.backend.controller;

import com.vlink.backend.auth.JwtUtil;
import com.vlink.backend.dto.*;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.email()))
            return ResponseEntity.badRequest().body("Este email já está registado.");

        User user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(req.role() != null ? req.role() : User.Role.VOLUNTEER);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "token",        jwtUtil.generateToken(user.getEmail(), user.getRole()),
            "refreshToken", jwtUtil.generateRefreshToken(user.getEmail(), user.getRole())
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        return userRepository.findByEmail(req.email())
            .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
            .map(u -> ResponseEntity.ok(Map.of(
                "token",        jwtUtil.generateToken(u.getEmail(), u.getRole()),
                "refreshToken", jwtUtil.generateRefreshToken(u.getEmail(), u.getRole())
            )))
            .orElse(ResponseEntity.status(401).body(Map.of("error", "Credenciais inválidas.")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest req) {
        if (!jwtUtil.isTokenValid(req.refreshToken()))
            return ResponseEntity.status(401).body(Map.of("error", "Sessão expirada. Faz login novamente."));

        String email = jwtUtil.extractEmail(req.refreshToken());
        String role  = jwtUtil.extractRole(req.refreshToken());

        return ResponseEntity.ok(Map.of(
            "token",        jwtUtil.generateToken(email, User.Role.valueOf(role)),
            "refreshToken", jwtUtil.generateRefreshToken(email, User.Role.valueOf(role))
        ));
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
                                       @RequestBody UpdateProfileRequest req) {
        return userRepository.findByEmail(auth.getName())
            .map(u -> {
                if (req.name() != null && !req.name().isBlank())
                    u.setName(req.name());
                if (req.password() != null && !req.password().isBlank())
                    u.setPassword(passwordEncoder.encode(req.password()));
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