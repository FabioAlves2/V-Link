package com.vlink.backend.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, String>> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "O evento foi alterado por outro pedido em simultâneo. Tenta novamente."));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, String>> handleTooLarge(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(Map.of("error", "O ficheiro excede o tamanho máximo permitido (5MB)."));
  }

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
