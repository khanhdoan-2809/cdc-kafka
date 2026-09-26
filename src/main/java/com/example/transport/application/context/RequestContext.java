package com.example.transport.application.context;

public record RequestContext(
        String actorId,
        ActorType actorType,
        String requestId,
        String correlationId
) {

    public boolean hasActor() {
        return actorId != null && !actorId.isBlank();
    }
}