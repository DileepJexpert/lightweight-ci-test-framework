package com.example.lightweight.domain;

import java.math.BigDecimal;

public record PaymentEvent(
        String eventId,
        EventType eventType,
        String correlationId,
        String paymentId,
        BigDecimal amount
) {
}
