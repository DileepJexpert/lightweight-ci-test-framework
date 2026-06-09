@prod-safe @smoke @performance @sla
Feature: Production response time SLA verification

  # PRODUCTION SAFE — read-only endpoints, no data written.
  # Purpose: verify that production response times are within agreed SLAs
  #          immediately after a deployment, before real traffic hits.
  # Catches: slow cold-start, under-provisioned pod resources,
  #          DB connection pool exhausted, external service latency spike.
  #
  # SLA thresholds defined here should match your production SLA agreement.
  # Adjust the 'assert responseTime < X' values per your contract.

  Background:
    * def newId = function(prefix){ return prefix + '-' + java.util.UUID.randomUUID() }

  Scenario: Health endpoint SLA — must respond under 500ms
    # Tightest SLA — health check is used by Kubernetes liveness probe.
    # If this is slow, Kubernetes may restart the pod unnecessarily.
    Given url baseUrl + '/actuator/health'
    When method get
    Then status 200
    * print 'Health SLA check — response time (ms):', responseTime
    * assert responseTime < 500

  Scenario: Service status endpoint SLA — must respond under 1000ms
    # Baseline read API performance — no DB call, no external dependency.
    # If this is slow, the JVM or network is the bottleneck.
    Given url baseUrl + '/api/customers/smoke/status'
    When method get
    Then status 200
    * print 'Status API SLA check — response time (ms):', responseTime
    * assert responseTime < 1000

  Scenario: Protected API SLA — auth + handler must respond under 1500ms
    # Includes the auth layer processing time.
    # If this is slow, the token validation mechanism or JWT library is the bottleneck.
    * if (!authToken) karate.abort()
    Given url baseUrl + '/api/protected/profile'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    * print 'Protected API SLA check — response time (ms):', responseTime
    * assert responseTime < 1500

  Scenario: Loan GET endpoint SLA — routing + service layer under 1000ms
    # A 404 still exercises the full Spring MVC stack: ingress → controller → service.
    # Verifies the request processing pipeline performs within SLA for read operations.
    Given url baseUrl + '/api/loans/prod-sla-probe-' + newId('check')
    And header x-correlation-id = 'prod-sla-' + newId('corr')
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 404
    * print 'Loan GET SLA check — response time (ms):', responseTime
    * assert responseTime < 1000
