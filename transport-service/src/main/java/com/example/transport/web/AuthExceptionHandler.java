package com.example.transport.web;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice(
        assignableTypes = {
            AuthController.class,
            SessionController.class,
            BrowserAuthController.class
        })
public class AuthExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> credentials(AuthenticationException exception) {
        if (exception
                instanceof
                org.springframework.security.authentication.AuthenticationServiceException)
            return unavailable();
        return ResponseEntity.status(401)
                .body(
                        Map.of(
                                "status",
                                401,
                                "error",
                                "Unauthorized",
                                "message",
                                "Invalid credentials"));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<?> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "status",
                                400,
                                "error",
                                "Bad Request",
                                "message",
                                "Invalid login request"));
    }

    @ExceptionHandler({
        org.springframework.dao.DataAccessException.class,
        org.springframework.transaction.TransactionException.class,
        org.springframework.security.oauth2.jwt.JwtEncodingException.class
    })
    public ResponseEntity<?> database(RuntimeException exception) {
        return unavailable();
    }

    private ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503)
                .body(
                        Map.of(
                                "status",
                                503,
                                "error",
                                "Service Unavailable",
                                "message",
                                "Authentication service unavailable"));
    }
}
