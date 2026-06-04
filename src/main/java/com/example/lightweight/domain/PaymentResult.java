package com.example.lightweight.domain;

public record PaymentResult(String correlationId, String paymentId, boolean processed) {
}
