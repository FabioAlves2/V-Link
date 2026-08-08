package com.vlink.backend.auth;

import com.vlink.backend.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, User.Role role) {
        return buildToken(email, role, expiration, "access");
    }

    public String generateRefreshToken(String email, User.Role role) {
        return buildToken(email, role, refreshExpiration, "refresh");
    }

    private String buildToken(String email, User.Role role, long ttl, String type) {
        return Jwts.builder()
            .setId(UUID.randomUUID().toString())
            .setSubject(email)
            .claim("role", role.name())
            .claim("type", type)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + ttl))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public String extractJti(String token) {
        return extractClaims(token).getId();
    }

    public boolean isAccessToken(String token) {
        return "access".equals(extractClaims(token).get("type", String.class));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(extractClaims(token).get("type", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            return extractClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    // Parse único e reutilizável para JwtAuthFilter — corre em TODOS os pedidos autenticados,
    // por isso vale a pena evitar repetir a verificação de assinatura 3-4x por pedido
    // (era o que isTokenValid + isAccessToken + extractEmail + extractRole faziam, cada um
    // com o seu próprio parseClaimsJws). Devolve null se o token for inválido/expirado.
    public Claims parseValidClaims(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().after(new Date()) ? claims : null;
        } catch (Exception e) {
            return null;
        }
    }
}