# Lightweight CI-Friendly Test Framework

Java 21 Maven test framework for CI/CD environments where Docker, Testcontainers, Kafka containers, embedded Kafka, WireMock, LocalStack, Redis, and other external processes cannot run.

This project is intentionally split into two layers:

- Component tests: JUnit 5, Mockito, AssertJ, Spring MockMvc. These run inside the JVM and are safe for build-stage CI.
- Smoke tests: Karate tests against an already deployed QA/SIT/UAT service. These are intended for post-deployment gates.

## Why No Docker Or Mock Server?

Restricted CI runners often cannot start Docker, containerized dependencies, embedded brokers, or mock server processes. This framework avoids those dependencies completely. Business decisions are tested with mocks, direct handler invocation, and MockMvc controller tests.

Use a full integration framework separately when you need real Kafka broker behavior, real topic serialization, consumer groups, retries, DLT wiring, containerized dependencies, or third-party HTTP contract behavior.

## Project Structure

```text
src/main/java/com/example/lightweight
  controller
  domain
  service
  client
  kafka
  repository
  validation
src/test/java/component
  rest
  service
  kafka
  client
  validation
  idempotency
src/test/java/karate
  runner
  features
  resources
src/test/resources
  karate-config.js
  payloads
  schemas
```

## Maven Profiles

Run local developer verification without any external service:

```bash
mvn clean test -Pcomponent
```

This is the default local check for this lightweight framework. It runs JUnit, Mockito, MockMvc, validation, service, idempotency, and direct Kafka-handler tests. It does not need Docker, Kafka, WireMock, or a running Spring Boot service.

Run CI-safe component tests only:

```bash
mvn clean test -Pcomponent
```

Run unit/component tests:

```bash
mvn clean test -Punit
```

Run deployed-environment Karate smoke tests:

```bash
mvn clean verify -Psmoke "-Dkarate.env=qa" "-Dservice.base-url=https://your-real-qa-service-host" "-Dauth.token=your-token"
```

Run Karate against a service running locally:

```bash
mvn clean verify -Psmoke "-Dkarate.env=qa" "-Dservice.base-url=http://localhost:8080"
```

Karate requires a real HTTP service URL. If no service is running at the configured URL, Karate should fail because smoke tests are meant to test a deployed or locally running service.

## Running The Local Demo Service

This repository includes a small Spring Boot demo application so developers can try the Karate smoke tests locally. It is not a replacement for your real microservice; it only exposes sample endpoints used by the smoke features.

Start the demo service in one terminal:

```bash
mvn spring-boot:run
```

The service starts on:

```text
http://localhost:8080
```

Verify it manually:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/customers/smoke/status
```

Run Karate against the local demo service from a second terminal:

```bash
mvn verify -Psmoke "-Dkarate.env=qa" "-Dservice.base-url=http://localhost:8080" "-Dauth.token=local-token"
```

The smoke suite includes a management-ready digital loan origination feature:

```text
src/test/java/karate/features/business/digital-loan-origination.feature
```

It demonstrates six complete business outcomes:

- straight-through approval with KYC, credit, fraud, underwriting, and offer generation
- automated rejection with traceable policy reasons
- manual review followed by reviewer approval
- asynchronous credit-bureau timeout recovery using Karate `retry until`
- duplicate REST submission protected by an idempotency key
- offer-generation failure followed by compensation and approval reversal

The feature also demonstrates reusable feature calls, dynamic test data, request chaining, headers, multiple REST methods, timeline assertions, tags, polling, and HTML reporting.

Run only the loan business flow:

```bash
mvn verify -Psmoke "-Dkarate.options=--tags @loan"
```

From IntelliJ:

1. Run `LightweightTestApplication`.
2. Run `KarateSmokeTestRunner`.
3. No VM options are needed for the local demo because Karate defaults to `http://localhost:8080`.

Optional VM options if you want to be explicit:

```text
-Dkarate.env=qa -Dservice.base-url=http://localhost:8080 -Dauth.token=local-token
```

Run both component and smoke tests:

```bash
mvn clean verify -Pall "-Dkarate.env=sit" "-Dservice.base-url=https://sit-service.example.internal"
```

Run selected Karate tags:

```bash
mvn verify -Psmoke -Dkarate.options="--tags @smoke --tags ~@negative"
```

## Running Only Karate Tests From IntelliJ

Karate tests in this project are deployed-environment smoke tests. They do not run during the normal component-test build. You can run only Karate from IntelliJ in either of these ways.

### Option 1: Run The Maven Smoke Profile

1. Open this folder in IntelliJ: `lightweight-ci-test-framework`.
2. Make sure IntelliJ uses JDK 21.
3. Open the Maven tool window.
4. Create or run this Maven command:

```bash
clean verify -Psmoke "-Dkarate.env=qa" "-Dservice.base-url=https://your-real-qa-service-host" "-Dauth.token=your-token"
```

Use your real QA/SIT/UAT service URL. If the endpoint does not need a token, omit `-Dauth.token=...`.

To run only selected Karate tags from IntelliJ Maven:

```bash
verify -Psmoke "-Dkarate.env=qa" "-Dservice.base-url=https://your-real-qa-service-host" "-Dkarate.options=--tags @smoke --tags ~@negative"
```

### Option 2: Run The JUnit Karate Runner

1. Open `src/test/java/karate/runner/KarateSmokeTestRunner.java`.
2. Right-click the class.
3. Select `Run KarateSmokeTestRunner`.
4. Edit the run configuration and add VM options:

```text
-Dkarate.env=qa -Dservice.base-url=https://your-real-qa-service-host -Dauth.token=your-token
```

To filter tags from the JUnit runner, add:

```text
-Dkarate.options="--tags @smoke"
```

### Important

Do not run individual `.feature` files directly unless the Karate IntelliJ plugin is configured correctly. The most reliable IntelliJ options are the Maven `smoke` profile or the JUnit runner class.

Karate reports are generated under:

```text
target/karate-reports
```

If you see this error, the runner is configured correctly but the target URL still needs to be set:

```text
Invalid smoke test base URL. Replace placeholders with a real URL or use http://localhost:8080 for the local demo service.
```

## CI/CD Usage

Build stage:

```bash
mvn clean test -Pcomponent
```

This runs:

- MockMvc controller tests
- service-layer decision tests
- third-party client failure behavior using Mockito
- Kafka handler tests by direct method invocation
- validation tests
- duplicate/idempotency tests
- loan approval/rejection business-flow service tests

Post-deployment stage:

```bash
mvn clean verify -Psmoke "-Dkarate.env=qa" "-Dservice.base-url=$SERVICE_BASE_URL" "-Dauth.token=$AUTH_TOKEN"
```

This runs Karate against an already deployed environment. It does not start Docker, Kafka, WireMock, or any local process.

## Configuration

Karate smoke properties:

| Purpose | System property | Environment variable |
| --- | --- | --- |
| Environment | `karate.env` | n/a |
| Service base URL | `service.base-url` | `SERVICE_BASE_URL` |
| Auth token | `auth.token` | `AUTH_TOKEN` |
| Connect timeout | `http.connect-timeout-ms` | n/a |
| Read timeout | `http.read-timeout-ms` | n/a |

Supported smoke environments:

- `qa`
- `sit`
- `uat`

## What Component Tests Demonstrate

- Valid REST request returns expected status and calls service layer.
- Invalid REST request returns `400` and does not call service.
- Unauthorized request returns `401`.
- KYC `VERIFIED` publishes `CUSTOMER_VERIFIED`.
- KYC `REJECTED` publishes `CUSTOMER_REJECTED`.
- third-party failure, timeout, or malformed response publishes error event.
- duplicate event does not call third-party client and does not publish duplicate output.
- Kafka consumer logic is tested by invoking handler methods directly, without Kafka.
- correlation ID is asserted from input request/event to output event.
- loan application flow moves from initiation to eligibility, underwriting, and final approval or rejection.

## Adding Component Tests

Add JUnit tests under `src/test/java/component/<category>`.

Prefer this pattern:

- mock third-party clients with Mockito
- mock Kafka publisher ports
- mock repository/idempotency ports
- invoke service or handler methods directly
- verify event type, topic, status, and correlation ID

## Adding Karate Smoke Tests

Add `.feature` files under `src/test/java/karate/features`.

Keep smoke tests:

- fast
- stable
- independent of old data
- limited to deployed API readiness
- free of Docker, mock servers, or local Kafka assumptions

## What Not To Test Here

Do not put these in this lightweight project:

- Kafka broker behavior
- consumer group and offset behavior
- real DLT routing
- retry timing against a live broker
- full end-to-end third-party integration
- Docker Compose flows
- Testcontainers flows
- WireMock server scenarios

Those belong in a full integration framework such as Project 1 or in a dedicated QA/SIT regression suite.

## Reports

JUnit reports:

```text
target/surefire-reports
```

Karate reports:

```text
target/karate-reports
```
