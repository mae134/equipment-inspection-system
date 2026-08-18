package com.mae134.equipmentinspection.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationException(
      MethodArgumentNotValidException exception) {

    Map<String, String> errors = new LinkedHashMap<>();

    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", HttpStatus.BAD_REQUEST.value());
    response.put("message", "Validation failed");
    response.put("errors", errors);

    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<Map<String, Object>> handleDuplicateResourceException(
      DuplicateResourceException exception) {

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", HttpStatus.CONFLICT.value());
    response.put("message", exception.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(
      ResourceNotFoundException exception) {

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", HttpStatus.NOT_FOUND.value());
    response.put("message", exception.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
      IllegalArgumentException exception) {

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", HttpStatus.BAD_REQUEST.value());
    response.put("message", exception.getMessage());

    return ResponseEntity.badRequest().body(response);
  }
}
