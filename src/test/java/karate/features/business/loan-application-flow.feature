@smoke @business @e2e @loan
Feature: Loan application business journey

  Scenario: Loan initiated by REST goes through eligibility, underwriting, and final approval
    * def correlationId = 'loan-smoke-' + java.util.UUID.randomUUID()
    Given url baseUrl + '/api/loans'
    And header x-correlation-id = correlationId
    And header Content-Type = 'application/json'
    And request
      """
      {
        "correlationId": "#(correlationId)",
        "customerId": "customer-approved",
        "requestedAmount": 750000,
        "creditScore": 760,
        "monthlyIncome": 90000,
        "monthlyDebt": 18000
      }
      """
    When method post
    Then status 202
    And match response.correlationId == correlationId
    And match response.status == 'APPROVED'
    And match response.decision == 'APPROVED'
    And match response.timeline[*].name == ['LOAN_INITIATED', 'ELIGIBILITY_CHECK', 'UNDERWRITING', 'FINAL_DECISION']
    And match response.timeline[*].outcome == ['PASS', 'PASS', 'PASS', 'APPROVED']
    * def loanId = response.loanId

    Given url baseUrl + '/api/loans/' + loanId
    When method get
    Then status 200
    And match response.loanId == loanId
    And match response.correlationId == correlationId
    And match response.reason == 'Loan approved after eligibility and underwriting'

  Scenario: Loan initiated by REST goes through policy checks and final rejection
    * def correlationId = 'loan-smoke-' + java.util.UUID.randomUUID()
    Given url baseUrl + '/api/loans'
    And header x-correlation-id = correlationId
    And header Content-Type = 'application/json'
    And request
      """
      {
        "correlationId": "#(correlationId)",
        "customerId": "customer-rejected",
        "requestedAmount": 1500000,
        "creditScore": 650,
        "monthlyIncome": 45000,
        "monthlyDebt": 25000
      }
      """
    When method post
    Then status 202
    And match response.correlationId == correlationId
    And match response.status == 'REJECTED'
    And match response.decision == 'REJECTED'
    And match response.timeline[*].name == ['LOAN_INITIATED', 'ELIGIBILITY_CHECK', 'UNDERWRITING', 'FINAL_DECISION']
    And match response.timeline[1].outcome == 'FAIL'
    And match response.timeline[2].outcome == 'FAIL'
    And match response.timeline[3].outcome == 'REJECTED'
    * def loanId = response.loanId

    Given url baseUrl + '/api/loans/' + loanId
    When method get
    Then status 200
    And match response.loanId == loanId
    And match response.reason == 'Loan rejected by policy checks'
