package com.example.lightweight.service;

import com.example.lightweight.domain.LoanApplicationRequest;
import com.example.lightweight.domain.LoanApplicationResult;
import com.example.lightweight.domain.LoanDecision;
import com.example.lightweight.domain.LoanStatus;
import com.example.lightweight.domain.LoanStep;
import com.example.lightweight.domain.ManualReviewRequest;
import com.example.lightweight.repository.LoanApplicationRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoanApplicationService implements AutoCloseable {
    private final LoanApplicationRepository repository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public LoanApplicationService(LoanApplicationRepository repository) {
        this.repository = repository;
    }

    public LoanApplicationResult initiate(LoanApplicationRequest request, String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> processNewApplication(request, idempotencyKey));
    }

    public LoanApplicationResult find(String loanId) {
        return repository.findByLoanId(loanId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Loan application not found: " + loanId));
    }

    public LoanApplicationResult review(String loanId, ManualReviewRequest review) {
        LoanApplicationResult current = find(loanId);
        if (current.status() != LoanStatus.MANUAL_REVIEW) {
            throw new IllegalStateException("Loan is not awaiting manual review");
        }
        boolean approved = "APPROVE".equalsIgnoreCase(review.action());
        List<LoanStep> timeline = append(current.timeline(), new LoanStep(
                "MANUAL_REVIEW_DECISION",
                approved ? LoanStatus.APPROVED : LoanStatus.REJECTED,
                approved ? "APPROVED" : "REJECTED",
                "Decision recorded by " + review.reviewer()
        ));
        LoanApplicationResult updated = approved
                ? approve(current, timeline, "Approved after manual review")
                : update(current, LoanStatus.REJECTED, LoanDecision.REJECTED, "Rejected by manual reviewer", null, timeline);
        repository.save(updated);
        return updated;
    }

    private LoanApplicationResult processNewApplication(LoanApplicationRequest request, String idempotencyKey) {
        String loanId = "loan-" + UUID.randomUUID();
        List<LoanStep> timeline = new ArrayList<>();
        timeline.add(step("LOAN_INITIATED", LoanStatus.INITIATED, "PASS", "Loan application received"));
        timeline.add(step("KYC_VALIDATION", LoanStatus.KYC_VERIFIED, "PASS", "Customer identity verified"));

        String mode = request.processingMode() == null ? "STANDARD" : request.processingMode().toUpperCase();
        if ("CREDIT_TIMEOUT".equals(mode)) {
            timeline.add(step("CREDIT_BUREAU", LoanStatus.CREDIT_CHECK_PENDING, "RETRY", "Credit bureau timed out; retry scheduled"));
            LoanApplicationResult pending = result(loanId, request, LoanStatus.CREDIT_CHECK_PENDING, LoanDecision.PENDING,
                    "Waiting for credit bureau retry", null, idempotencyKey, timeline);
            repository.save(pending);
            scheduler.schedule(() -> completeAfterCreditRetry(loanId, request), 900, TimeUnit.MILLISECONDS);
            return pending;
        }

        timeline.add(step("CREDIT_BUREAU", LoanStatus.CREDIT_CHECK_COMPLETED, "PASS", "Credit report received"));
        return continueDecision(loanId, request, idempotencyKey, timeline, mode);
    }

    private void completeAfterCreditRetry(String loanId, LoanApplicationRequest request) {
        LoanApplicationResult current = find(loanId);
        List<LoanStep> timeline = append(current.timeline(),
                step("CREDIT_BUREAU_RETRY", LoanStatus.CREDIT_CHECK_COMPLETED, "PASS", "Credit report received on retry"));
        repository.save(continueDecision(loanId, request, current.idempotencyKey(), timeline, "STANDARD"));
    }

    private LoanApplicationResult continueDecision(String loanId, LoanApplicationRequest request, String idempotencyKey,
                                                     List<LoanStep> timeline, String mode) {
        timeline = append(timeline, step("FRAUD_SCREENING", LoanStatus.FRAUD_SCREENED,
                "FRAUD_HIT".equals(mode) ? "FAIL" : "PASS",
                "FRAUD_HIT".equals(mode) ? "Fraud indicators detected" : "No fraud indicators detected"));
        if ("FRAUD_HIT".equals(mode)) {
            LoanApplicationResult rejected = result(loanId, request, LoanStatus.REJECTED, LoanDecision.REJECTED,
                    "Rejected by fraud screening", null, idempotencyKey,
                    append(timeline, step("FINAL_DECISION", LoanStatus.REJECTED, "REJECTED", "Fraud policy rejection")));
            repository.save(rejected);
            return rejected;
        }

        boolean eligible = request.creditScore() >= 700 && request.monthlyIncome().compareTo(BigDecimal.valueOf(50000)) >= 0;
        timeline = append(timeline, step("ELIGIBILITY_CHECK", LoanStatus.ELIGIBILITY_CHECKED,
                eligible ? "PASS" : "FAIL", eligible ? "Eligibility policy satisfied" : "Credit score or income below threshold"));

        BigDecimal debtRatio = request.monthlyDebt().divide(request.monthlyIncome(), 4, RoundingMode.HALF_UP);
        boolean underwritingPassed = eligible && debtRatio.compareTo(BigDecimal.valueOf(0.40)) <= 0
                && request.requestedAmount().compareTo(request.monthlyIncome().multiply(BigDecimal.valueOf(20))) <= 0;
        timeline = append(timeline, step("UNDERWRITING", LoanStatus.UNDERWRITING_COMPLETED,
                underwritingPassed ? "PASS" : "FAIL", underwritingPassed ? "Risk and affordability accepted" : "Affordability policy failed"));

        if ("MANUAL_REVIEW".equals(mode)) {
            LoanApplicationResult review = result(loanId, request, LoanStatus.MANUAL_REVIEW, LoanDecision.MANUAL_REVIEW,
                    "Application requires human approval", null, idempotencyKey,
                    append(timeline, step("MANUAL_REVIEW", LoanStatus.MANUAL_REVIEW, "PENDING", "Borderline application queued")));
            repository.save(review);
            return review;
        }
        if (!underwritingPassed) {
            LoanApplicationResult rejected = result(loanId, request, LoanStatus.REJECTED, LoanDecision.REJECTED,
                    "Loan rejected by policy checks", null, idempotencyKey,
                    append(timeline, step("FINAL_DECISION", LoanStatus.REJECTED, "REJECTED", "Automated underwriting rejection")));
            repository.save(rejected);
            return rejected;
        }

        LoanApplicationResult base = result(loanId, request, LoanStatus.APPROVED, LoanDecision.APPROVED,
                "Loan approved after all automated checks", null, idempotencyKey, timeline);
        if ("OFFER_FAILURE".equals(mode)) {
            LoanApplicationResult reversed = update(base, LoanStatus.APPROVAL_REVERSED, LoanDecision.REVERSED,
                    "Approval reversed because offer generation failed", null,
                    append(timeline,
                            step("OFFER_GENERATION", LoanStatus.APPROVAL_REVERSED, "FAIL", "Offer service failed"),
                            step("COMPENSATION", LoanStatus.APPROVAL_REVERSED, "COMPLETED", "Approval reservation released")));
            repository.save(reversed);
            return reversed;
        }
        LoanApplicationResult approved = approve(base, timeline, "Loan approved after all automated checks");
        repository.save(approved);
        return approved;
    }

    private LoanApplicationResult approve(LoanApplicationResult base, List<LoanStep> timeline, String reason) {
        String offerId = "offer-" + UUID.randomUUID();
        return update(base, LoanStatus.APPROVED, LoanDecision.APPROVED, reason, offerId,
                append(timeline,
                        step("FINAL_DECISION", LoanStatus.APPROVED, "APPROVED", reason),
                        step("OFFER_GENERATION", LoanStatus.OFFER_GENERATED, "PASS", "Offer " + offerId + " generated")));
    }

    private LoanApplicationResult result(String loanId, LoanApplicationRequest request, LoanStatus status, LoanDecision decision,
                                         String reason, String offerId, String idempotencyKey, List<LoanStep> timeline) {
        return new LoanApplicationResult(loanId, request.correlationId(), request.customerId(), status, decision,
                reason, offerId, idempotencyKey, List.copyOf(timeline));
    }

    private LoanApplicationResult update(LoanApplicationResult base, LoanStatus status, LoanDecision decision,
                                         String reason, String offerId, List<LoanStep> timeline) {
        return new LoanApplicationResult(base.loanId(), base.correlationId(), base.customerId(), status, decision,
                reason, offerId, base.idempotencyKey(), List.copyOf(timeline));
    }

    private LoanStep step(String name, LoanStatus status, String outcome, String reason) {
        return new LoanStep(name, status, outcome, reason);
    }

    private List<LoanStep> append(List<LoanStep> existing, LoanStep... steps) {
        List<LoanStep> copy = new ArrayList<>(existing);
        copy.addAll(List.of(steps));
        return copy;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
