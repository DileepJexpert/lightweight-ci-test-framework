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
mvn clean verify -Psmoke -Dkarate.env=qa -Dservice.base-url=https://qa-service.example.internal -Dauth.token=replace-me
```

Run both component and smoke tests:

```bash
mvn clean verify -Pall -Dkarate.env=sit -Dservice.base-url=https://sit-service.example.internal
```

Run selected Karate tags:

```bash
mvn verify -Psmoke -Dkarate.options="--tags @smoke --tags ~@negative"
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

Post-deployment stage:

```bash
mvn clean verify -Psmoke -Dkarate.env=qa -Dservice.base-url=$SERVICE_BASE_URL -Dauth.token=$AUTH_TOKEN
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
