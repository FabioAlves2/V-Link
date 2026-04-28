package com.vlink.backend.dto;

import com.vlink.backend.model.User;

public record RegisterRequest(String name, String email, String password, User.Role role) {} 