package com.example.lightweight.domain;

import java.util.List;

public record LoanApplicationResult(
        String loanId,
        String correlationId,
        String customerId,
        LoanStatus status,
        LoanDecision decision,
        String reason,
        List<LoanStep> timeline
) {
}
