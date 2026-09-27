package com.example.auditconsumer.application.port;

import com.example.auditconsumer.domain.AuditEvent;

public interface AuditLogStore {

    AuditSaveResult save(AuditEvent event);
}