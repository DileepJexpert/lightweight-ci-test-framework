@smoke @business @e2e @loan
Feature: Digital loan origination management journey

  Background:
    * def newId = function(prefix){ return prefix + '-' + java.util.UUID.randomUUID() }
    * def submitLoan =
      """
      function(mode, score, income, debt, amount, idempotencyKey) {
        var correlationId = newId('loan-corr');
        var payload = {
          correlationId: correlationId,
          customerId: newId('customer'),
          requestedAmount: amount,
          creditScore: score,
          monthlyIncome: income,
          monthlyDebt: debt,
          processingMode: mode
        };
        var result = karate.call('classpath:karate/features/business/helpers/submit-loan.feature',
          { baseUrl: baseUrl, correlationId: correlationId, idempotencyKey: idempotencyKey, payload: payload });
        return result.response;
      }
      """

  Scenario: Straight-through approval completes KYC, credit, fraud, underwriting, and offer generation
    * def application = submitLoan('STANDARD', 780, 110000, 18000, 900000, newId('idem'))
    * match application.correlationId == '#string'
    * match application.status == 'APPROVED'
    * match application.decision == 'APPROVED'
    * match application.offerId == '#string'
    * match application.timeline[*].name contains only
      """
      ['LOAN_INITIATED', 'KYC_VALIDATION', 'CREDIT_BUREAU', 'FRAUD_SCREENING',
       'ELIGIBILITY_CHECK', 'UNDERWRITING', 'FINAL_DECISION', 'OFFER_GENERATION']
      """
    * match application.timeline[*].outcome == ['PASS', 'PASS', 'PASS', 'PASS', 'PASS', 'PASS', 'APPROVED', 'PASS']

    Given url baseUrl + '/api/loans/' + application.loanId
    When method get
    Then status 200
    And match response.loanId == application.loanId
    And match response.offerId == application.offerId

  Scenario: Affordability failure creates a traceable automated rejection
    * def application = submitLoan('STANDARD', 650, 45000, 26000, 1500000, newId('idem'))
    * match application.status == 'REJECTED'
    * match application.decision == 'REJECTED'
    * match application.offerId == null
    * match application.timeline[4].outcome == 'FAIL'
    * match application.timeline[5].outcome == 'FAIL'
    * match application.timeline[6].outcome == 'REJECTED'

  Scenario: Borderline application is approved by manual review and receives an offer
    * def application = submitLoan('MANUAL_REVIEW', 710, 65000, 22000, 650000, newId('idem'))
    * match application.status == 'MANUAL_REVIEW'
    * match application.decision == 'MANUAL_REVIEW'

    Given url baseUrl + '/api/loans/' + application.loanId + '/review'
    And request { reviewer: 'credit-manager-42', action: 'APPROVE' }
    When method post
    Then status 200
    And match response.status == 'APPROVED'
    And match response.offerId == '#string'
    And match response.timeline[*].name contains 'MANUAL_REVIEW_DECISION'

  Scenario: Credit bureau timeout recovers asynchronously through retry
    * def application = submitLoan('CREDIT_TIMEOUT', 775, 100000, 16000, 800000, newId('idem'))
    * match application.status == 'CREDIT_CHECK_PENDING'
    * match application.decision == 'PENDING'

    Given url baseUrl + '/api/loans/' + application.loanId
    And retry until response.status == 'APPROVED'
    When method get
    Then status 200
    And match response.offerId == '#string'
    And match response.timeline[*].name contains 'CREDIT_BUREAU_RETRY'

  Scenario: Duplicate REST submission is idempotent and returns the original loan
    * def idempotencyKey = newId('idem-duplicate')
    * def first = submitLoan('STANDARD', 780, 110000, 18000, 900000, idempotencyKey)
    * def duplicate = submitLoan('STANDARD', 780, 110000, 18000, 900000, idempotencyKey)
    * match duplicate.loanId == first.loanId
    * match duplicate.correlationId == first.correlationId
    * match duplicate.idempotencyKey == idempotencyKey
    * match duplicate.timeline == first.timeline

  Scenario: Offer generation failure triggers compensation and reverses approval
    * def application = submitLoan('OFFER_FAILURE', 790, 120000, 15000, 950000, newId('idem'))
    * match application.status == 'APPROVAL_REVERSED'
    * match application.decision == 'REVERSED'
    * match application.offerId == null
    * match application.timeline[*].name contains 'OFFER_GENERATION'
    * match application.timeline[*].name contains 'COMPENSATION'
    * match application.timeline[-1].outcome == 'COMPLETED'
