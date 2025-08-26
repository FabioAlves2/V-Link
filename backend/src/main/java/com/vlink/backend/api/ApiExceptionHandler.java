package com.vlink.backend.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
    // mensagem simples; em prod podes inspecionar a causa para mensagens mais específicas
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "Email já existe", "code", "USER_EMAIL_CONFLICT"));
  }
}

