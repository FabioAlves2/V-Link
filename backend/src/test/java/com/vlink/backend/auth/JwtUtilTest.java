package com.vlink.backend.auth;

import com.vlink.backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-not-for-production-use-0123456789");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604800000L);
    }

    @Test
    void accessTokenIsTypedAsAccess() {
        String token = jwtUtil.generateToken("user@example.com", User.Role.VOLUNTEER);
        assertThat(jwtUtil.isAccessToken(token)).isTrue();
        assertThat(jwtUtil.isRefreshToken(token)).isFalse();
    }

    @Test
    void refreshTokenIsTypedAsRefresh() {
        String token = jwtUtil.generateRefreshToken("user@example.com", User.Role.VOLUNTEER);
        assertThat(jwtUtil.isRefreshToken(token)).isTrue();
        assertThat(jwtUtil.isAccessToken(token)).isFalse();
    }

    @Test
    void tokensCarryEmailRoleAndUniqueJti() {
        String access = jwtUtil.generateToken("user@example.com", User.Role.PROMOTER);
        String refresh = jwtUtil.generateRefreshToken("user@example.com", User.Role.PROMOTER);

        assertThat(jwtUtil.extractEmail(access)).isEqualTo("user@example.com");
        assertThat(jwtUtil.extractRole(access)).isEqualTo("PROMOTER");
        assertThat(jwtUtil.extractJti(access)).isNotBlank();
        assertThat(jwtUtil.extractJti(access)).isNotEqualTo(jwtUtil.extractJti(refresh));
    }

    @Test
    void tokenIsValidUntilExpiry() {
        String token = jwtUtil.generateToken("user@example.com", User.Role.VOLUNTEER);
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void malformedTokenIsInvalid() {
        assertThat(jwtUtil.isTokenValid("not-a-real-token")).isFalse();
    }
}
