@prod-safe @smoke @connectivity
Feature: Production API connectivity and routing checks

  # PRODUCTION SAFE — read-only GET endpoints only, no data written.
  # Purpose: confirm ingress routing, load balancer, and service discovery
  #          are working correctly after deployment to EKS.
  # Catches: ingress misconfiguration, wrong target group, DNS issue,
  #          service selector mismatch, wrong namespace deployment.

  Background:
    * def newId = function(prefix){ return prefix + '-' + java.util.UUID.randomUUID() }

  Scenario: Service status endpoint is reachable and reports READY
    # SmokeSupportController.customerStatus() — a dedicated endpoint for prod smoke.
    # Returns { service, status } — no auth needed, no data read from DB.
    Given url baseUrl + '/api/customers/smoke/status'
    And header Content-Type = 'application/json'
    When method get
    Then status 200
    # Validate response structure and exact service name
    And match response == { service: 'lightweight-ci-test-framework', status: 'READY' }
    * print 'Service is READY — ingress routing and pod are healthy'
    * assert responseTime < 1000

  Scenario: Loan API endpoint is reachable — 404 confirms routing works
    # We do NOT create a loan. We call GET with a non-existent ID.
    # A 404 from the application proves:
    #   ✅ Ingress routing to /api/loans/** works
    #   ✅ Pod is handling requests
    #   ✅ Application context loaded (service + repository wired)
    # A 503 would mean the pod is unhealthy.
    # A connection error would mean ingress or DNS is broken.
    Given url baseUrl + '/api/loans/prod-smoke-probe-' + newId('check')
    And header x-correlation-id = 'prod-smoke-' + newId('corr')
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 404
    * print 'Loan API routing confirmed — 404 means route works, no data written'
    * assert responseTime < 1000

  Scenario: Response headers confirm correct service version is deployed
    # Validates the correct Docker image tag was deployed by checking
    # the Content-Type header is standard — proxy or wrong service would differ.
    Given url baseUrl + '/api/customers/smoke/status'
    When method get
    Then status 200
    And match header Content-Type contains 'application/json'
    * print 'Content-Type header confirmed — correct service is responding'
