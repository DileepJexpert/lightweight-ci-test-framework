# Karate Framework — Demo Presenter Guide

> **How to use this file**
> Walk through the feature files in the order below.
> Each section tells you: what to open, which lines to point at, and what to say out loud.

---

## Demo Flow Overview

| # | Feature File | Karate Capability | Wow Factor |
|---|---|---|---|
| 1 | `health.feature` | Basic HTTP call | Warmup — zero Java |
| 2 | `auth.feature` | Headers, negative test, `karate.abort()` | Auth in 15 lines |
| 3 | `digital-loan-origination.feature` | Full E2E, `retry until`, `karate.call()` | Real business journey |
| 4 | `loan-eligibility-data-driven.feature` | Scenario Outline + Examples table | Data-driven with no Java |
| 5 | `loan-schema-validation.feature` | `#regex`, `match each`, `#?` predicates | Type-safe without code |
| 6 | `loan-external-data.feature` | `read()`, `karate.map()`, `karate.filter()` | External data + JS power |
| 7 | `loan-response-headers.feature` | `match header`, `responseTime`, SLA gates | Non-functional testing |
| 8 | `bulk-callback.feature` | `table`, `karate.map()`, `karate.forEach()` | Bulk ops, no loops |
| 9 | `karate-config.js` | `configure headers`, `callSingle` pattern | Enterprise-grade config |

---

## 1 — Basic HTTP Call
**File:** `src/test/java/karate/features/smoke/health.feature`

```gherkin
Given url baseUrl + '/actuator/health'
When method get
Then status 200
```

**What to say:**
- "This is the entire test. No imports, no class, no annotations."
- "Given / When / Then maps directly to HTTP: set up → call → assert."
- "A non-developer can read and write this."

---

## 2 — Headers, Auth, Negative Testing, `karate.abort()`
**File:** `src/test/java/karate/features/smoke/auth.feature`

**Lines to highlight:**
```gherkin
Scenario: Protected endpoint rejects missing authentication
  Given url baseUrl + '/api/protected/profile'
  When method get
  Then status 401                          # ← negative test — expect failure

Scenario: Protected endpoint accepts configured token
  * if (!authToken) karate.abort()         # ← skip gracefully if no token configured
  And header Authorization = 'Bearer ' + authToken
  Then status 200
```

**What to say:**
- "Negative tests are identical to positive tests — just assert 4xx instead."
- "`karate.abort()` skips the scenario cleanly when a precondition isn't met. No flaky skip annotations."
- "The auth token comes from `karate-config.js` — one place, all features inherit it."

---

## 3 — Full E2E Business Journey
**File:** `src/test/java/karate/features/business/digital-loan-origination.feature`

### 3a — Reusable helper via `karate.call()`
**Lines to highlight (Background):**
```gherkin
* def result = karate.call(
    'classpath:karate/features/business/helpers/submit-loan.feature',
    { baseUrl: baseUrl, correlationId: correlationId, payload: payload }
  )
```
**Say:** "`karate.call()` is like calling a method, but it's a whole feature file. No Spring context, no base class."

### 3b — Array assertions
```gherkin
* match application.timeline[*].name contains only
  ['LOAN_INITIATED', 'KYC_VALIDATION', 'CREDIT_BUREAU', ...]
```
**Say:** "Assert the entire pipeline in one line. `contains only` verifies exact membership regardless of order."

### 3c — `retry until` for async
```gherkin
And retry until response.status == 'APPROVED'
When method get
```
**Say:** "Polls the endpoint until the condition is met, up to the configured retry count. No `Awaitility`, no while-loop, no sleep."

### 3d — Idempotency test (same key, same result)
```gherkin
* def first     = submitLoan(... idempotencyKey)
* def duplicate = submitLoan(... idempotencyKey)
* match duplicate.loanId == first.loanId
```
**Say:** "Two API calls, one assertion. Proves idempotency without any server-side mocking."

---

## 4 — Data-Driven Testing (Scenario Outline)
**File:** `src/test/java/karate/features/business/loan-eligibility-data-driven.feature`

**Lines to highlight:**
```gherkin
Scenario Outline: <profile> borrower receives <expectedStatus> decision
  * def application = submitLoan('<mode>', <score>, <income>, <debt>, <amount>, newId('idem'))
  * match application.status == '<expectedStatus>'

  Examples:
    | profile    | mode           | score | income | expectedStatus       |
    | prime      | STANDARD       | 780   | 110000 | APPROVED             |
    | subprime   | STANDARD       | 650   | 45000  | REJECTED             |
    | borderline | MANUAL_REVIEW  | 710   | 65000  | MANUAL_REVIEW        |
    | timeout    | CREDIT_TIMEOUT | 775   | 100000 | CREDIT_CHECK_PENDING |
    | offer-fail | OFFER_FAILURE  | 790   | 120000 | APPROVAL_REVERSED    |
```

**What to say:**
- "One scenario, five test cases. Each row runs independently with its own name in the report."
- "Numbers in the table (`780`, `110000`) are evaluated as JS numbers — no casting, no type issues."
- "Add a new profile? Add a row. No Java change needed."
- "The `profile` column is just a label — it becomes the scenario name in the HTML report."

---

## 5 — Schema & Type Validation
**File:** `src/test/java/karate/features/business/loan-schema-validation.feature`

### 5a — Full type schema
```gherkin
* match application contains
  """
  {
    loanId:   '#regex [A-Za-z0-9\\-]+',
    offerId:  '#string',
    timeline: '#[] #object'
  }
  """
```
**Say:** "`#string`, `#number`, `#boolean`, `#null`, `#array`, `#object`, `#regex` are built-in type markers. No external JSON Schema library needed."

### 5b — `match each` + `#?` predicates
```gherkin
* match each application.timeline == { name: '#? _.length > 0', outcome: '#string' }
```
**Say:** "`match each` runs the schema against every element in the array. `#? _.length > 0` is an inline JS predicate — the `_` is the field value."

### 5c — `karate.jsonPath()`
```gherkin
* def compensationStep = karate.jsonPath(application, "$.timeline[?(@.name == 'COMPENSATION')]")
* assert compensationStep.length == 1
```
**Say:** "Full JsonPath expressions for complex extractions — filter arrays by condition, just like SQL WHERE on JSON."

---

## 6 — External Data Files + Array Operations
**File:** `src/test/java/karate/features/business/loan-external-data.feature`
**Data:** `src/test/resources/karate/testdata/borrowers.json`

### 6a — `read()`
```gherkin
* def borrowers = read('classpath:karate/testdata/borrowers.json')
* def profile   = borrowers.profiles.prime
```
**Say:** "`read()` loads JSON, YAML, CSV, or plain JS files. Test data lives outside the feature file — QA owns the data, dev owns the logic."

### 6b — `karate.map()` and `karate.filter()`
```gherkin
* def results  = karate.map(borrowers.bulkSubmissions, function(profile){ ... })
* def approved = karate.filter(results, function(r){ return r.status == 'APPROVED' })
* assert approved.length == 2
```
**Say:** "Transform and filter arrays with JS functions inline. This is equivalent to Java Stream `.map().filter()` but without a single line of Java."

---

## 7 — Response Headers + Performance SLA
**File:** `src/test/java/karate/features/business/loan-response-headers.feature`

### 7a — `match header`
```gherkin
And match header Content-Type contains 'application/json'
```
**Say:** "Header name is case-insensitive. `contains` does a substring match — handles charsets like `application/json; charset=utf-8`."

### 7b — `responseTime` SLA
```gherkin
* print 'Response time (ms):', responseTime
* assert responseTime < 3000
```
**Say:** "`responseTime` is a built-in variable — milliseconds from sending the request to receiving the full response. One line turns a functional test into a performance gate."

### 7c — Per-feature `configure headers`
```gherkin
* configure headers =
  """
  function() {
    return { 'x-request-id': 'req-' + java.util.UUID.randomUUID() };
  }
  """
```
**Say:** "Override global headers for just this feature. The function runs before every HTTP call — every request gets a fresh UUID automatically."

---

## 8 — Bulk Operations: `table`, `karate.map()`, `karate.forEach()`
**File:** `src/test/java/karate/features/rest/bulk-callback.feature`

### 8a — `table` keyword
```gherkin
* table customers
  | correlationId  | customerId     | firstName | email                |
  | newId('corr')  | newId('cust')  | 'Alice'   | 'alice@example.test' |
  | newId('corr')  | newId('cust')  | 'Bob'     | 'bob@example.test'   |
```
**Say:** "`table` builds a JSON array from a Gherkin table. Each cell is a Karate expression — function calls, variables, and string literals all work."

### 8b — `match each` on a plain array
```gherkin
* match each statusCodes == 400
```
**Say:** "After submitting invalid payloads, this asserts every single response was a 400. One line replaces a for-loop."

### 8c — `karate.forEach()` for side effects
```gherkin
* karate.forEach(accepted, function(r){ karate.log('Accepted:', r.customerId) })
```
**Say:** "`forEach` is for side effects — logging, setting state — where you don't need a return value. Keeps map and filter semantically clean."

---

## 9 — Enterprise Configuration (`karate-config.js`)
**File:** `src/test/java/karate/resources/karate-config.js`

### 9a — `configure headers` globally
```javascript
karate.configure('headers', function() {
  return { 'x-request-id': 'auto-' + java.util.UUID.randomUUID() };
});
```
**Say:** "This injects a fresh tracking header on every single HTTP request across the entire suite. Zero per-scenario setup."

### 9b — `callSingle` pattern (show the comment block)
```javascript
// var session = karate.callSingle(
//   'classpath:karate/features/auth/get-token.feature',
//   { baseUrl: baseUrl, authToken: authToken }
// );
// authToken = session.token;
```
**Say:** "`callSingle` runs ONCE per JVM session — not once per scenario, not once per feature, once total. Use it to fetch an OAuth token so you don't hammer your auth server with 200 requests during a full suite run."

---

## Quick Answers to Audience Questions

| Question | Answer |
|---|---|
| "Can it test non-REST APIs?" | Yes — SOAP/XML, GraphQL, gRPC (via HTTP), WebSocket (with plugin). |
| "Can it run in CI/CD?" | Yes — it's just `mvn verify -Psmoke`. No browser, no Docker needed. |
| "Can it run in parallel?" | Yes — `Runner.parallel(threads, ...)` in the JUnit runner. Reports are merged automatically. |
| "How do we handle OAuth?" | `callSingle` in `karate-config.js` fetches the token once, all scenarios share it. |
| "What about dynamic auth tokens?" | `configure headers` function runs per request — recalculate the token there. |
| "Can QA write these without Java?" | Yes — the only Java is the one-line runner class. Everything else is `.feature` + `.json`. |
| "How do we debug?" | `print` statement logs to console. `karate.log()` logs with context. HTML report shows every request/response. |
| "Does it generate reports?" | Yes — Karate generates an HTML report with request/response bodies, timings, and pass/fail for every scenario. |
