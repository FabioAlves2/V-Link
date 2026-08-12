package com.vlink.backend.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

// Estende ResponseEntityExceptionHandler (em vez de só @RestControllerAdvice numa classe simples)
// para herdar o tratamento correto (400/404/405, não 500) de excepções conhecidas do Spring MVC
// (JSON inválido, rota sem handler, método não suportado, etc.) — sem isto, o catch-all
// genérico abaixo (Exception.class) intercetava-as primeiro e devolvia sempre 500.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Map<String, String>> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "O evento foi alterado por outro pedido em simultâneo. Tenta novamente."));
  }

  // As duas seguintes são overrides (não novos @ExceptionHandler) das versões herdadas de
  // ResponseEntityExceptionHandler — mesma assinatura exata, só para substituir o corpo da
  // resposta; um novo @ExceptionHandler para o mesmo tipo já coberto pela superclasse dava
  // erro de arranque por mapeamento ambíguo.
  @Override
  protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(Map.of("error", "O ficheiro excede o tamanho máximo permitido (5MB)."));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
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
    // Tem de vir antes do check de subscriptions abaixo: a tabela favorites usa as mesmas
    // colunas user_id/event_id, por isso sem esta ordem uma violação de favoritos era
    // classificada (incorretamente) como um conflito de inscrição.
    if (detail.contains("favorite")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "Este evento já está nos teus favoritos", "code", "FAVORITE_CONFLICT"));
    }
    if (detail.contains("user_id") && detail.contains("event_id")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "Já estás inscrito neste evento", "code", "SUBSCRIPTION_CONFLICT"));
    }
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "Conflito de dados.", "code", "DATA_CONFLICT"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "Ocorreu um erro inesperado. Tenta novamente mais tarde."));
  }
}
