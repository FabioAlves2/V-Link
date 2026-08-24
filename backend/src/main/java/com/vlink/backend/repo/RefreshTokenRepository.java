package com.vlink.backend.repo;

import com.vlink.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByJti(String jti);

    // Chamado ao mudar a password (ver AuthController.updateMe) — sem isto, um refresh token
    // roubado antes da troca de password continuava válido pelos 7 dias inteiros, tornando a
    // troca de password inútil como resposta a um comprometimento suspeito da conta.
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userEmail = :email AND r.revoked = false")
    void revokeAllForUser(@Param("email") String email);
}
