@ignore
Feature: Submit a loan application

  Scenario:
    Given url baseUrl + '/api/loans'
    And header x-correlation-id = correlationId
    And header Idempotency-Key = idempotencyKey
    And header Content-Type = 'application/json'
    And request payload
    When method post
    Then status 202
    And match response.idempotencyKey == idempotencyKey
