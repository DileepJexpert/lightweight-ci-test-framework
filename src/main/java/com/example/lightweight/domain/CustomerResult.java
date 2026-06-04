package com.example.lightweight.domain;

public record CustomerResult(String correlationId, String customerId, CustomerStatus status) {
}
