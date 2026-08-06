package com.vlink.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "O email não pode estar vazio.") String email,
    @NotBlank(message = "A password não pode estar vazia.") String password
) {}
