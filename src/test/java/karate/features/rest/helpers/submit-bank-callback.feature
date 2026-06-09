@ignore
Feature: Submit a single bank verification callback (reusable helper)

  # Called via karate.call() from bulk-callback.feature.
  # Expected args: { baseUrl, correlationId, requestBody }
  # Returns:       { statusCode }

  Scenario:
    Given url baseUrl + '/callbacks/bank/verifications'
    And header x-correlation-id = correlationId
    And header Content-Type     = 'application/json'
    And request requestBody
    When method post
    * def statusCode = responseStatus
