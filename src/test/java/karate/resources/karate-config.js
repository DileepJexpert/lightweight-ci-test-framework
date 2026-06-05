function fn() {
  var env = karate.env || 'qa';
  var baseUrl = karate.properties['service.base-url'] || java.lang.System.getenv('SERVICE_BASE_URL') || 'http://localhost:8080';
  if (baseUrl.indexOf('example.internal') !== -1 || baseUrl.indexOf('replace-me') !== -1) {
    throw 'Invalid smoke test base URL. Replace placeholders with a real URL or use http://localhost:8080 for the local demo service.';
  }

  karate.configure('connectTimeout', Number(karate.properties['http.connect-timeout-ms'] || '5000'));
  karate.configure('readTimeout', Number(karate.properties['http.read-timeout-ms'] || '15000'));
  karate.configure('retry', { count: 10, interval: 250 });
  karate.configure('logPrettyRequest', true);
  karate.configure('logPrettyResponse', true);

  return {
    env: env,
    baseUrl: baseUrl,
    authToken: karate.properties['auth.token'] || java.lang.System.getenv('AUTH_TOKEN') || 'local-token',
    defaultHeaders: {
      'Content-Type': 'application/json',
      'x-correlation-id': 'smoke-' + java.util.UUID.randomUUID()
    }
  };
}
