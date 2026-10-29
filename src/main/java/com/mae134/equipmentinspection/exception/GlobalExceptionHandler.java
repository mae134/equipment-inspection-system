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
  public ResponseEntity<ValidationErrorResponse> handleValidationException(
      MethodArgumentNotValidException exception) {

    Map<String, String> errors = new LinkedHashMap<>();

    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

    ValidationErrorResponse response =
        new ValidationErrorResponse(HttpStatus.BAD_REQUEST.value(), "Validation failed", errors);

    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ApiErrorResponse> handleDuplicateResourceException(
      DuplicateResourceException exception) {

    ApiErrorResponse response =
        new ApiErrorResponse(HttpStatus.CONFLICT.value(), exception.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
      ResourceNotFoundException exception) {

    ApiErrorResponse response =
        new ApiErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
      IllegalArgumentException exception) {

    ApiErrorResponse response =
        new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), exception.getMessage());

    return ResponseEntity.badRequest().body(response);
  }
}
