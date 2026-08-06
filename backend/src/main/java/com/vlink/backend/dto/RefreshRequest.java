package com.vlink.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank(message = "O refresh token não pode estar vazio.") String refreshToken
) {}
