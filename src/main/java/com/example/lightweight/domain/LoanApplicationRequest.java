package com.example.lightweight.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record LoanApplicationRequest(
        @NotBlank String correlationId,
        @NotBlank String customerId,
        @Positive BigDecimal requestedAmount,
        @Min(300) int creditScore,
        @Positive BigDecimal monthlyIncome,
        @Positive BigDecimal monthlyDebt
) {
}
