@smoke @rest @qa @sit
Feature: Salesforce callback deployed smoke checks

  Scenario: Valid Salesforce callback is accepted
    * def correlationId = 'smoke-' + java.util.UUID.randomUUID()
    Given url baseUrl + '/callbacks/salesforce/customers'
    And header Authorization = authToken ? 'Bearer ' + authToken : 'Bearer smoke-token'
    And header x-correlation-id = correlationId
    And header Content-Type = 'application/json'
    And request
      """
      {
        "correlationId": "#(correlationId)",
        "customerId": "smoke-customer",
        "firstName": "Smoke",
        "lastName": "Test",
        "email": "smoke.customer@example.test",
        "source": "SALESFORCE"
      }
      """
    When method post
    Then status 202
    And match response.correlationId == correlationId

  @negative
  Scenario: Invalid Salesforce callback is rejected
    * def correlationId = 'smoke-' + java.util.UUID.randomUUID()
    Given url baseUrl + '/callbacks/salesforce/customers'
    And header Authorization = authToken ? 'Bearer ' + authToken : 'Bearer smoke-token'
    And header x-correlation-id = correlationId
    And header Content-Type = 'application/json'
    And request
      """
      {
        "correlationId": "#(correlationId)",
        "customerId": "",
        "email": "invalid"
      }
      """
    When method post
    Then status 400
