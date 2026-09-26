package com.example.transport.infrastructure.persistence;

import com.example.transport.application.context.CurrentRequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RequestContextAuditorAware implements AuditorAware<String> {

    private final CurrentRequestContext currentRequestContext;

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.ofNullable(
                currentRequestContext.get().actorId()
        );
    }
}
