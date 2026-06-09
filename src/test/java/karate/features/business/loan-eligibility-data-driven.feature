@smoke @business @data-driven @loan
Feature: Loan eligibility decisions across borrower profiles

  # Demonstrates: Scenario Outline, Examples table (parametrised / data-driven testing)
  # Each row in the Examples table produces an independent test run with its own name.

  Background:
    * def newId = function(prefix){ return prefix + '-' + java.util.UUID.randomUUID() }
    * def submitLoan =
      """
      function(mode, score, income, debt, amount, idempotencyKey) {
        var correlationId = newId('loan-corr');
        var payload = {
          correlationId: correlationId,
          customerId:    newId('customer'),
          requestedAmount: amount,
          creditScore:   score,
          monthlyIncome: income,
          monthlyDebt:   debt,
          processingMode: mode
        };
        var result = karate.call(
          'classpath:karate/features/business/helpers/submit-loan.feature',
          { baseUrl: baseUrl, correlationId: correlationId, idempotencyKey: idempotencyKey, payload: payload }
        );
        return result.response;
      }
      """

  # <score>, <income> etc. are substituted directly from the Examples row.
  # No quoting needed for numbers — Karate evaluates the expression as JS.
  Scenario Outline: <profile> borrower receives <expectedStatus> decision
    * def application = submitLoan('<mode>', <score>, <income>, <debt>, <amount>, newId('idem'))
    * match application.status   == '<expectedStatus>'
    * match application.decision == '<expectedDecision>'
    * print 'Profile: <profile> | Status:', application.status, '| Decision:', application.decision

    Examples:
      | profile          | mode           | score | income | debt  | amount  | expectedStatus       | expectedDecision |
      | prime            | STANDARD       | 780   | 110000 | 18000 | 900000  | APPROVED             | APPROVED         |
      | subprime         | STANDARD       | 650   | 45000  | 26000 | 1500000 | REJECTED             | REJECTED         |
      | borderline       | MANUAL_REVIEW  | 710   | 65000  | 22000 | 650000  | MANUAL_REVIEW        | MANUAL_REVIEW    |
      | timeout-recovery | CREDIT_TIMEOUT | 775   | 100000 | 16000 | 800000  | CREDIT_CHECK_PENDING | PENDING          |
      | offer-failure    | OFFER_FAILURE  | 790   | 120000 | 15000 | 950000  | APPROVAL_REVERSED    | REVERSED         |
