package com.vlink.backend.dto;

import jakarta.validation.constraints.NotNull;

public record AttendanceRequest(
    @NotNull(message = "O campo checkedIn é obrigatório.") Boolean checkedIn
) {}
