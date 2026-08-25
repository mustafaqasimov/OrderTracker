package com.mustafaqasimov.ordertracker.exception;

import com.mustafaqasimov.ordertracker.exception.error.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.error("Resource not found", ex);
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex) {
        log.error("Resource already exists: ", ex);
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.error("Invalid credentials", ex);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnAuthorized(UnauthorizedException ex) {
        log.error("Unauthorized access", ex);
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidOperation(InvalidOperationException ex) {
        log.warn("Invalid operation: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        StringBuilder sb = new StringBuilder();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                sb.append(fe.getField()).append(": ").append(fe.getDefaultMessage()).append("; "));
        String msg = sb.length() > 0 ? sb.substring(0, sb.length() - 2) : "Validation failed";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, msg);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);

        return new ResponseEntity<>(body, status);
    }
}
