package com.carDekhoAI.common.exception;

import com.carDekhoAI.car.service.CarNotFoundException;
import com.carDekhoAI.car.tool.DatabaseQueryException;
import com.carDekhoAI.chat.orchestrator.ConversationNotFoundException;
import com.carDekhoAI.llm.client.LlmException;
import com.carDekhoAI.preference.agent.PreferenceExtractionException;
import com.carDekhoAI.sql.agent.SqlGenerationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({CarNotFoundException.class, ConversationNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        log.error("Resource not found - path={}, error={}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                            HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.error("Validation failed - path={}, errors={}", request.getRequestURI(), message);
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", message, request);
    }

    @ExceptionHandler(SqlGenerationException.class)
    public ResponseEntity<ErrorResponse> handleSqlGeneration(SqlGenerationException ex, HttpServletRequest request) {
        log.error("SQL generation failed - path={}, error={}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid SQL", ex.getMessage(), request);
    }

    @ExceptionHandler({LlmException.class, PreferenceExtractionException.class, DatabaseQueryException.class})
    public ResponseEntity<ErrorResponse> handleUpstreamDependencyFailure(RuntimeException ex,
                                                                          HttpServletRequest request) {
        log.error("Upstream dependency failure - path={}, type={}, error={}",
                request.getRequestURI(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
        String label = ex instanceof DatabaseQueryException ? "Database Failure" : "LLM Failure";
        return build(HttpStatus.SERVICE_UNAVAILABLE, label, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception - path={}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message,
                                                 HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), error, message, request.getRequestURI()));
    }
}
