@prod-safe @smoke @auth
Feature: Production authentication layer validation

  # PRODUCTION SAFE — read-only, validates auth config only, no data written.
  # Purpose: confirm the auth layer is correctly configured after deployment.
  # Catches: wrong secret mounted, token validation broken, security config issue.
  #
  # These are the most valuable prod-safe tests after health checks —
  # a misconfigured auth layer is a security incident, not just a bug.

  Scenario: Unauthenticated request is rejected with 401
    # Confirms the security layer is active and blocking unauthenticated access.
    # If this returns 200, the auth config is broken — a critical security failure.
    Given url baseUrl + '/api/protected/profile'
    When method get
    Then status 401
    And match response.status == 'UNAUTHORIZED'
    * print 'Auth rejection confirmed — security layer is active'
    * assert responseTime < 500

  Scenario: Valid Bearer token is accepted
    # Confirms the configured production auth token works end-to-end.
    # Validates: token is mounted correctly in the pod via Kubernetes Secret.
    # If this returns 401, the EKS secret is wrong or missing.
    * if (!authToken) karate.fail('authToken is not configured — check EKS_AUTH_TOKEN secret')
    Given url baseUrl + '/api/protected/profile'
    And header Authorization = 'Bearer ' + authToken
    When method get
    Then status 200
    And match response.status == 'AUTHORIZED'
    * print 'Auth token accepted — EKS secret mounted correctly'
    * assert responseTime < 1000

  Scenario: Malformed Authorization header is rejected
    # Validates the auth layer rejects badly formed tokens, not just absent ones.
    # A properly configured service should return 401 for these cases.
    Given url baseUrl + '/api/protected/profile'
    And header Authorization = 'InvalidFormat no-bearer-prefix'
    When method get
    Then status 401
    * print 'Malformed auth header correctly rejected'
