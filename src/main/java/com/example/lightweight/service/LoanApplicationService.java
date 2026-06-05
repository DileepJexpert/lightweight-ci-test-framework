package com.example.lightweight.service;

import com.example.lightweight.domain.LoanApplicationRequest;
import com.example.lightweight.domain.LoanApplicationResult;
import com.example.lightweight.domain.LoanDecision;
import com.example.lightweight.domain.LoanStatus;
import com.example.lightweight.domain.LoanStep;
import com.example.lightweight.repository.LoanApplicationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoanApplicationService {
    private final LoanApplicationRepository repository;

    public LoanApplicationService(LoanApplicationRepository repository) {
        this.repository = repository;
    }

    public LoanApplicationResult initiate(LoanApplicationRequest request) {
        String loanId = "loan-" + UUID.randomUUID();
        List<LoanStep> timeline = new ArrayList<>();
        timeline.add(new LoanStep("LOAN_INITIATED", LoanStatus.INITIATED, "PASS", "Loan application received"));

        boolean eligible = request.creditScore() >= 700
                && request.monthlyIncome().compareTo(BigDecimal.valueOf(50000)) >= 0;
        timeline.add(new LoanStep(
                "ELIGIBILITY_CHECK",
                LoanStatus.ELIGIBILITY_CHECKED,
                eligible ? "PASS" : "FAIL",
                eligible ? "Credit score and income satisfy eligibility policy" : "Credit score or income is below policy threshold"
        ));

        BigDecimal debtRatio = request.monthlyDebt()
                .divide(request.monthlyIncome(), 4, RoundingMode.HALF_UP);
        boolean underwritingPassed = eligible
                && debtRatio.compareTo(BigDecimal.valueOf(0.40)) <= 0
                && request.requestedAmount().compareTo(request.monthlyIncome().multiply(BigDecimal.valueOf(20))) <= 0;
        timeline.add(new LoanStep(
                "UNDERWRITING",
                LoanStatus.UNDERWRITING_COMPLETED,
                underwritingPassed ? "PASS" : "FAIL",
                underwritingPassed ? "Debt ratio and exposure are acceptable" : "Debt ratio or requested exposure is too high"
        ));

        LoanDecision decision = underwritingPassed ? LoanDecision.APPROVED : LoanDecision.REJECTED;
        LoanStatus status = underwritingPassed ? LoanStatus.APPROVED : LoanStatus.REJECTED;
        String reason = underwritingPassed ? "Loan approved after eligibility and underwriting" : "Loan rejected by policy checks";
        timeline.add(new LoanStep("FINAL_DECISION", status, decision.name(), reason));

        LoanApplicationResult result = new LoanApplicationResult(
                loanId,
                request.correlationId(),
                request.customerId(),
                status,
                decision,
                reason,
                List.copyOf(timeline)
        );
        repository.save(result);
        return result;
    }

    public LoanApplicationResult find(String loanId) {
        return repository.findByLoanId(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application not found: " + loanId));
    }
}
