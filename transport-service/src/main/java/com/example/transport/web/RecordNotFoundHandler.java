package com.example.transport.web;

import com.example.transport.application.RecordNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class RecordNotFoundHandler {
    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<?> notFound(
            RecordNotFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(404)
                .body(
                        Map.of(
                                "status",
                                404,
                                "error",
                                "Not Found",
                                "message",
                                exception.getMessage(),
                                "timestamp",
                                Instant.now().toString(),
                                "path",
                                request.getRequestURI()));
    }
}
