@smoke @auth @negative @qa @sit
Feature: Deployed service authentication smoke checks

  Scenario: Protected endpoint rejects missing authentication
    Given url baseUrl + '/api/protected/profile'
    When method get
    Then status 401

  Scenario: Protected endpoint accepts configured token
    * if (!authToken) karate.abort()
    Given url baseUrl + '/api/protected/profile'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
