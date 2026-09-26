package com.example.auditconsumer.domain;

public enum EntityType {

    TRANSPORT,
    JOB,
    CONTAINER;

    public static EntityType fromTable(String table) {
        return switch (table) {
            case "transport" -> TRANSPORT;
            case "job" -> JOB;
            case "container" -> CONTAINER;
            default -> throw new IllegalArgumentException("Unsupported CDC table: " + table);
        };
    }
}