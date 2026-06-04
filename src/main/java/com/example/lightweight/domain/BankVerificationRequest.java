package com.example.lightweight.domain;

import jakarta.validation.constraints.NotBlank;

public record BankVerificationRequest(
        @NotBlank String correlationId,
        @NotBlank String customerId,
        @NotBlank String accountNumber,
        @NotBlank String requestType
) {
}
