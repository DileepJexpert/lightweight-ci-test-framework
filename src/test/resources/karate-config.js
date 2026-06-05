function fn() {
  var env = karate.env || 'qa';
  var baseUrl = karate.properties['service.base-url'] || java.lang.System.getenv('SERVICE_BASE_URL');
  if (!baseUrl || baseUrl.indexOf('example.internal') !== -1 || baseUrl.indexOf('replace-me') !== -1) {
    throw 'Missing real smoke test base URL. Set -Dservice.base-url=https://your-' + env + '-service-host or SERVICE_BASE_URL before running Karate.';
  }

  karate.configure('connectTimeout', Number(karate.properties['http.connect-timeout-ms'] || '5000'));
  karate.configure('readTimeout', Number(karate.properties['http.read-timeout-ms'] || '15000'));
  karate.configure('logPrettyRequest', true);
  karate.configure('logPrettyResponse', true);

  return {
    env: env,
    baseUrl: baseUrl,
    authToken: karate.properties['auth.token'] || java.lang.System.getenv('AUTH_TOKEN') || '',
    defaultHeaders: {
      'Content-Type': 'application/json',
      'x-correlation-id': 'smoke-' + java.util.UUID.randomUUID()
    }
  };
}
