package com.example.lightweight.repository;

import com.example.lightweight.domain.LoanApplicationResult;

import java.util.Optional;

public interface LoanApplicationRepository {
    void save(LoanApplicationResult result);

    Optional<LoanApplicationResult> findByLoanId(String loanId);
}
