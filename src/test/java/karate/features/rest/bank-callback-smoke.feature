@smoke @rest @qa @sit
Feature: Bank callback deployed smoke checks

  Scenario: Valid bank callback receives a synchronous response
    * def correlationId = 'smoke-' + java.util.UUID.randomUUID()
    Given url baseUrl + '/callbacks/bank/verifications'
    And header x-correlation-id = correlationId
    And header Content-Type = 'application/json'
    And request
      """
      {
        "correlationId": "#(correlationId)",
        "customerId": "smoke-bank-customer",
        "accountNumber": "smoke-account",
        "requestType": "CUSTOMER_VERIFICATION"
      }
      """
    When method post
    Then status 200
    And match response.correlationId == correlationId
