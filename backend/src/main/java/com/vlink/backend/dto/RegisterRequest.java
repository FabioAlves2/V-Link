package com.vlink.backend.dto;

import com.vlink.backend.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "O nome não pode estar vazio.") @Size(max = 100, message = "O nome não pode ter mais de 100 caracteres.") String name,
    @NotBlank(message = "O email não pode estar vazio.") @Email(message = "Email inválido.") @Size(max = 150, message = "O email não pode ter mais de 150 caracteres.") String email,
    @NotBlank(message = "A password não pode estar vazia.") @Size(min = 6, message = "A password deve ter pelo menos 6 caracteres.") String password,
    User.Role role
) {}
