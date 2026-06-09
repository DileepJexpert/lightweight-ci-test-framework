function fn() {
  var env     = karate.env || 'qa';
  var baseUrl = karate.properties['service.base-url'] || java.lang.System.getenv('SERVICE_BASE_URL') || 'http://localhost:8080';

  if (baseUrl.indexOf('example.internal') !== -1 || baseUrl.indexOf('replace-me') !== -1) {
    throw 'Invalid smoke test base URL. Replace placeholders with a real URL or use http://localhost:8080 for the local demo service.';
  }

  var authToken = karate.properties['auth.token'] || java.lang.System.getenv('AUTH_TOKEN') || 'local-token';

  // ── Timeouts & retry ──────────────────────────────────────────────────────
  karate.configure('connectTimeout', Number(karate.properties['http.connect-timeout-ms'] || '5000'));
  karate.configure('readTimeout',    Number(karate.properties['http.read-timeout-ms']    || '15000'));
  karate.configure('retry', { count: 10, interval: 250 });

  // ── Logging ───────────────────────────────────────────────────────────────
  karate.configure('logPrettyRequest',  true);
  karate.configure('logPrettyResponse', true);

  // ── configure headers (dynamic global headers) ────────────────────────────
  // The function is called before EVERY HTTP request in the suite.
  // Injects a fresh x-request-id per call for distributed tracing.
  // Individual scenarios can override or extend these headers as needed.
  karate.configure('headers', function() {
    return {
      'x-request-id': 'auto-' + java.util.UUID.randomUUID()
    };
  });

  // ── karate.callSingle() — suite-level setup (runs once per JVM session) ───
  // Uncomment to validate service health and initialise a shared auth session
  // before any scenario runs. The result is cached across all parallel threads.
  //
  // var session  = karate.callSingle(
  //   'classpath:karate/features/auth/get-token.feature',
  //   { baseUrl: baseUrl, authToken: authToken }
  // );
  // authToken = session.token;  // override with token fetched from the service

  return {
    env:       env,
    baseUrl:   baseUrl,
    authToken: authToken,
    defaultHeaders: {
      'Content-Type':    'application/json',
      'x-correlation-id': 'smoke-' + java.util.UUID.randomUUID()
    }
  };
}
