@smoke @qa @sit
Feature: Deployed service health smoke checks

  Scenario: Health endpoint is available
    Given url baseUrl + '/actuator/health'
    When method get
    Then status 200
    And match response.status == 'UP'
