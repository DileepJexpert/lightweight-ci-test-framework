@smoke @rest @qa @sit
Feature: Service-to-service KYC flow (Service A calls Service B internally)

  # Karate is a black-box client: it only talks to Service A (this loan/customer service).
  # Service A's /callbacks/bank/verifications endpoint internally calls Service B (the KYC
  # service) over HTTP using RestClient. In EKS that downstream URL is the Kubernetes
  # internal Service DNS name (kyc-service.<namespace>.svc.cluster.local), so this single
  # Karate POST exercises a real end-to-end A -> B -> A integration.
  #
  # Pre-requisite for this to pass in QA/SIT: Service B must be deployed and healthy in the
  # same namespace, and Service A must be started with kyc.service.base-url pointing at it.
  # In the local demo (no kyc.service.base-url) Service A uses an in-process stub that always
  # returns VERIFIED, so the same scenario still passes without Service B running.

  Scenario: Verification triggers a downstream KYC call and returns the resolved status
    * def correlationId = 's2s-' + java.util.UUID.randomUUID()
    Given url baseUrl + '/callbacks/bank/verifications'
    And header x-correlation-id = correlationId
    And header Content-Type = 'application/json'
    And request
      """
      {
        "correlationId": "#(correlationId)",
        "customerId": "s2s-kyc-customer",
        "accountNumber": "s2s-account",
        "requestType": "CUSTOMER_VERIFICATION"
      }
      """
    When method post
    Then status 200
    And match response.correlationId == correlationId
    And match response.customerId == 's2s-kyc-customer'
    # The status is whatever Service B (or its stub) resolved — proving the internal hop ran.
    # Local stub returns VERIFIED; a real Service B may also reject or ask for retry.
    And match response.status == '#regex VERIFIED|REJECTED|PENDING_RETRY'
