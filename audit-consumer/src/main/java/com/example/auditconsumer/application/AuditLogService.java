package com.example.auditconsumer.application;

import com.example.auditconsumer.application.port.AuditLogStore;
import com.example.auditconsumer.application.port.AuditSaveResult;
import com.example.auditconsumer.domain.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogStore auditLogStore;

    @Transactional
    public AuditSaveResult save(AuditEvent event) {
        return auditLogStore.save(event);
    }
}