@ignore
Feature: Fetch / validate auth session — designed for karate.callSingle()

  # Demonstrates: karate.callSingle() pattern
  #
  # PURPOSE
  # -------
  # karate.callSingle() executes a feature ONCE per JVM session regardless of how
  # many scenarios or parallel threads are running.  Its result is cached and shared.
  # Use it for expensive operations: OAuth token fetch, environment health check,
  # seed data creation.
  #
  # USAGE IN karate-config.js
  # -------------------------
  #   var session = karate.callSingle(
  #     'classpath:karate/features/auth/get-token.feature',
  #     { baseUrl: baseUrl, authToken: authToken }
  #   );
  #   // session.token is now available globally as a shared variable
  #
  # In a real OAuth environment, replace the health check below with a POST to
  # your token endpoint and extract the access_token from the response.

  Scenario: Confirm service is reachable and initialise shared auth token
    Given url baseUrl + '/actuator/health'
    When method get
    Then status 200
    And match response.status == 'UP'
    * print 'Service health confirmed — auth session initialised'

    # In real usage: POST /oauth/token → extract response.access_token
    # Here the pre-configured token is returned directly (demo service).
    * def token = authToken
