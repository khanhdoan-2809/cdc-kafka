package com.example.auditconsumer.application.exception;

public class InvalidCdcEventException extends RuntimeException {

    public InvalidCdcEventException(String message) {
        super(message);
    }

    public InvalidCdcEventException(String message, Throwable cause) {
        super(message, cause);
    }
}