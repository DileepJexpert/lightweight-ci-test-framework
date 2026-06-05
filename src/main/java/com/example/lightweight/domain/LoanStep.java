package com.example.lightweight.domain;

public record LoanStep(
        String name,
        LoanStatus status,
        String outcome,
        String reason
) {
}
