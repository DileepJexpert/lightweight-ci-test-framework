package com.example.lightweight.domain;

public record CustomerEvent(
        String eventId,
        EventType eventType,
        String correlationId,
        String customerId
) {
}
