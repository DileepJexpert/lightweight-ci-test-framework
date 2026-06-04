@smoke @rest @qa @sit
Feature: Basic deployed API smoke checks

  Scenario: Read-only customer status endpoint responds
    Given url baseUrl + '/api/customers/smoke/status'
    And headers defaultHeaders
    When method get
    Then status 200
    And match response contains { service: '#string' }
