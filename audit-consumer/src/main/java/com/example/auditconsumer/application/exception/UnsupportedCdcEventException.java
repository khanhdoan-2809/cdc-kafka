package com.example.auditconsumer.application.exception;

public class UnsupportedCdcEventException extends RuntimeException {

    public UnsupportedCdcEventException(String message) {
        super(message);
    }

    public UnsupportedCdcEventException(String message, Throwable cause) {
        super(message, cause);
    }
}