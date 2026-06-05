package com.example.lightweight.controller;

import com.example.lightweight.domain.LoanApplicationRequest;
import com.example.lightweight.domain.LoanApplicationResult;
import com.example.lightweight.domain.ManualReviewRequest;
import com.example.lightweight.service.LoanApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loans")
public class LoanApplicationController {
    private final LoanApplicationService service;

    public LoanApplicationController(LoanApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LoanApplicationResult> initiate(
            @RequestHeader("x-correlation-id") String correlationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @org.springframework.web.bind.annotation.RequestBody LoanApplicationRequest request
    ) {
        LoanApplicationRequest enrichedRequest = new LoanApplicationRequest(
                correlationId,
                request.customerId(),
                request.requestedAmount(),
                request.creditScore(),
                request.monthlyIncome(),
                request.monthlyDebt(),
                request.processingMode()
        );
        String effectiveIdempotencyKey = idempotencyKey == null ? correlationId : idempotencyKey;
        return ResponseEntity.accepted().body(service.initiate(enrichedRequest, effectiveIdempotencyKey));
    }

    @GetMapping("/{loanId}")
    public LoanApplicationResult get(@PathVariable("loanId") String loanId) {
        return service.find(loanId);
    }

    @PostMapping("/{loanId}/review")
    public LoanApplicationResult review(
            @PathVariable("loanId") String loanId,
            @Valid @org.springframework.web.bind.annotation.RequestBody ManualReviewRequest request
    ) {
        return service.review(loanId, request);
    }
}
