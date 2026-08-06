package com.vlink.backend.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "Dados inválidos.", "errors", errors));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
    String detail = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();

    if (detail.contains("email")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "Email já existe", "code", "USER_EMAIL_CONFLICT"));
    }
    if (detail.contains("user_id") && detail.contains("event_id")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "Já estás inscrito neste evento", "code", "SUBSCRIPTION_CONFLICT"));
    }
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "Conflito de dados.", "code", "DATA_CONFLICT"));
  }
}
