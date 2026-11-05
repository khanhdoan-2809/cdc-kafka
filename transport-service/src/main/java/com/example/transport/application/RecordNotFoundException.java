package com.example.transport.application;

public class RecordNotFoundException extends RuntimeException {
    public RecordNotFoundException() {
        super("Record not found");
    }
}
