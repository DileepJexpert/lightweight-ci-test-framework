@smoke @business @headers @performance @loan
Feature: Loan API response headers and performance SLA assertions

  # Demonstrates:
  #   - match header  — assert specific response header values
  #   - responseTime  — built-in Karate variable (milliseconds since request was sent)
  #   - assert on responseTime for SLA / performance gates
  #   - configure headers — show a per-feature override of global configured headers

  Background:
    * def newId = function(prefix){ return prefix + '-' + java.util.UUID.randomUUID() }

    # configure headers with a JS function: executed before EVERY HTTP request in this feature.
    # Injects a fresh x-request-id on each call — common in enterprise API tracing.
    # This overrides / extends the global configure headers set in karate-config.js.
    * configure headers =
      """
      function() {
        return {
          'Content-Type': 'application/json',
          'x-request-id': 'req-' + java.util.UUID.randomUUID()
        };
      }
      """

  Scenario: Submit loan — verify Content-Type header and response within SLA
    * def corrId = newId('hdr-corr')
    * def idemKey = newId('idem')

    Given url baseUrl + '/api/loans'
    And header x-correlation-id = corrId
    And header Idempotency-Key  = idemKey
    And request
      """
      {
        "correlationId":   "#(corrId)",
        "customerId":      "hdr-test-customer",
        "requestedAmount": 900000,
        "creditScore":     780,
        "monthlyIncome":   110000,
        "monthlyDebt":     18000,
        "processingMode":  "STANDARD"
      }
      """
    When method post
    Then status 202

    # match header — case-insensitive header name, partial value match via 'contains'
    And match header Content-Type contains 'application/json'

    # responseTime is a built-in Karate variable (milliseconds).
    # Use it as a lightweight performance / SLA gate in smoke tests.
    * print 'POST /api/loans response time (ms):', responseTime
    * assert responseTime < 3000

  Scenario: GET loan status — Content-Type header and tighter SLA for read operations
    # Step 1: create a loan so we have a valid loanId
    * def corrId  = newId('perf-corr')
    * def idemKey = newId('idem')

    Given url baseUrl + '/api/loans'
    And header x-correlation-id = corrId
    And header Idempotency-Key  = idemKey
    And request
      """
      {
        "correlationId":   "#(corrId)",
        "customerId":      "perf-customer",
        "requestedAmount": 500000,
        "creditScore":     780,
        "monthlyIncome":   110000,
        "monthlyDebt":     18000,
        "processingMode":  "STANDARD"
      }
      """
    When method post
    Then status 202
    * def loanId = response.loanId

    # Step 2: GET the loan and check headers + response time
    Given url baseUrl + '/api/loans/' + loanId
    When method get
    Then status 200
    And match header Content-Type contains 'application/json'
    * print 'GET /api/loans/:id response time (ms):', responseTime
    * assert responseTime < 1000

  Scenario: Health endpoint responds within strict SLA
    Given url baseUrl + '/actuator/health'
    When method get
    Then status 200
    And match header Content-Type contains 'application/json'
    * print 'Health check response time (ms):', responseTime
    * assert responseTime < 500
