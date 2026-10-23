package com.prabhakar.orbit.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.prabhakar.orbit.api.dto.ApiError;
import com.prabhakar.orbit.service.InvalidJobRequestException;
import com.prabhakar.orbit.service.JobNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed JSON request", List.of());
    }

    @ExceptionHandler(InvalidJobRequestException.class)
    ResponseEntity<ApiError> handleInvalidJob(InvalidJobRequestException ex) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(JobNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(JobNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, details));
    }
}
