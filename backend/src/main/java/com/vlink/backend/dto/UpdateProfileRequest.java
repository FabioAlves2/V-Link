package com.vlink.backend.dto;

public record UpdateProfileRequest(String name, String currentPassword, String password) {}