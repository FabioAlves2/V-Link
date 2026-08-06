package com.vlink.backend.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100, message = "O nome não pode ter mais de 100 caracteres.") String name,
    String currentPassword,
    @Size(min = 6, message = "A password deve ter pelo menos 6 caracteres.") String password
) {}
