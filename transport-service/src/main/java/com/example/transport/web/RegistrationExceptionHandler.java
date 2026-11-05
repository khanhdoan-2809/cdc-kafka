package com.example.transport.web;

import com.example.transport.security.*;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationExceptionHandler {
    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
    }

    @ExceptionHandler(RegistrationUnavailableException.class)
    public ResponseEntity<?> disabled(RegistrationUnavailableException exception) {
        return error(404, "Registration is unavailable");
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ResponseEntity<?> token(InvalidVerificationTokenException exception) {
        return error(400, "Invalid or expired verification token");
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<?> invalid(Exception exception) {
        return error(400, "Invalid registration request");
    }
}
