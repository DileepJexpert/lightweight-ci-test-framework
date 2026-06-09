@ignore
Feature: Submit a single Salesforce customer callback (reusable helper)

  # Called via karate.call() / karate.map() from bulk-callback.feature.
  # Expected args: { baseUrl, authToken, customer }
  # Returns:       { statusCode }

  Scenario:
    * def body =
      """
      {
        correlationId: '#(customer.correlationId)',
        customerId:    '#(customer.customerId)',
        firstName:     '#(customer.firstName)',
        lastName:      '#(customer.lastName)',
        email:         '#(customer.email)',
        source:        '#(customer.source)'
      }
      """
    Given url baseUrl + '/callbacks/salesforce/customers'
    And header Authorization  = 'Bearer ' + authToken
    And header x-correlation-id = customer.correlationId
    And header Content-Type   = 'application/json'
    And request body
    When method post
    # Capture numeric status code so callers can assert it
    * def statusCode = responseStatus
