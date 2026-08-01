package com.qaforge.bootstrap.error;

import com.qaforge.domain.exception.LlmParseException;
import com.qaforge.domain.exception.PrNotFoundException;
import com.qaforge.domain.exception.QaForgeException;
import jakarta.validation.ConstraintViolationException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Central error mapping for the REST API, per PRD §16.1 and the error table in §12.1. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PrNotFoundException.class)
    public ResponseEntity<ErrorResponse> prNotFound(PrNotFoundException ex) {
        return ResponseEntity.unprocessableEntity()
            .body(new ErrorResponse("PR_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(LlmParseException.class)
    public ResponseEntity<ErrorResponse> llmParse(LlmParseException ex) {
        log.error("LLM parse failure in agent {}: {}", ex.getAgentName(), ex.getRawResponse());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("LLM_PARSE_ERROR", "Agent " + ex.getAgentName() + " returned invalid JSON"));
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> timeout(TimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(new ErrorResponse("TIMEOUT", "Pipeline exceeded the configured timeout"));
    }

    @ExceptionHandler(QaForgeException.class)
    public ResponseEntity<ErrorResponse> qaForge(QaForgeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ErrorResponse("UPSTREAM_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> validation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> methodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Invalid request");
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
