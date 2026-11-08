package com.example.transport.security;

import lombok.RequiredArgsConstructor;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrowserSessionCleanup {
    private final BrowserAuthSettings settings;
    private final BrowserSessionService sessions;

    @Scheduled(
            fixedDelayString = "${auth.browser.cleanup-delay-ms:3600000}",
            initialDelayString = "${auth.browser.cleanup-delay-ms:3600000}")
    public void cleanup() {
        if (!settings.enabled()) return;
        try {
            sessions.cleanupExpired();
        } catch (org.springframework.dao.DataAccessException unavailable) {
            LoggerFactory.getLogger(BrowserSessionCleanup.class)
                    .warn("Expired browser session cleanup unavailable; will retry");
        }
    }
}
