@prod-safe @smoke @health
Feature: Production health and readiness checks

  # PRODUCTION SAFE — read-only, no data created, no state changed.
  # Purpose: confirm the deployed pod is alive, ready to serve traffic,
  #          and responding within SLA after every production deployment.
  #
  # Run command:
  #   mvn verify -Psmoke -Dkarate.env=production \
  #     -Dservice.base-url=https://loan-api.company.com \
  #     -Dkarate.options="--tags @prod-safe"

  Scenario: Health endpoint reports service is UP
    # Spring Boot Actuator health check — the first thing to verify after deploy.
    # If this fails, the pod is running but the application context is broken.
    Given url baseUrl + '/actuator/health'
    When method get
    Then status 200
    And match response.status == 'UP'
    # SLA: health check must respond in under 500ms in production
    * print 'Health check response time (ms):', responseTime
    * assert responseTime < 500

  Scenario: Liveness — service has not entered a broken state
    # Spring Boot liveness probe: reports if the app has crashed internally.
    # Kubernetes uses this to decide whether to restart the container.
    Given url baseUrl + '/actuator/health/liveness'
    When method get
    Then status 200
    And match response.status == 'UP'
    * assert responseTime < 500

  Scenario: Readiness — service is ready to accept traffic
    # Spring Boot readiness probe: reports if the app is ready to handle requests.
    # Kubernetes uses this to decide whether to route traffic to the pod.
    # This is the last check GoCD does before Karate smoke tests begin (Stage 3).
    Given url baseUrl + '/actuator/health/readiness'
    When method get
    Then status 200
    And match response.status == 'ACCEPTING_TRAFFIC'
    * assert responseTime < 500
