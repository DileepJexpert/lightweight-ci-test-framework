# Karate Framework — Demo Presenter Guide

> **How to use this file**
> Walk through the feature files in the order below.
> Each section tells you: what to open, which lines to point at, and what to say out loud.

---

## Demo Flow Overview (11 feature files)

| # | Feature File | Karate Capability | Wow Factor |
|---|---|---|---|
| 1 | `health.feature` | Basic HTTP GET + status assert | Warmup — zero Java |
| 2 | `basic-api.feature` | `match contains` on response body, `defaultHeaders` | Partial response assertion |
| 3 | `auth.feature` | Auth headers, negative test, `karate.abort()` | Auth in 15 lines |
| 4 | `bank-callback-smoke.feature` | POST with correlation ID, response echo | Callback contract in 10 lines |
| 5 | `salesforce-callback-smoke.feature` | Bearer token auth, negative 400 validation | Auth + validation together |
| 6 | `digital-loan-origination.feature` | Full E2E, `retry until`, `karate.call()`, `contains only` | Real business journey |
| 7 | `loan-eligibility-data-driven.feature` | Scenario Outline + Examples table | Data-driven with no Java |
| 8 | `loan-schema-validation.feature` | `#regex`, `match each`, `#?` predicates, `karate.jsonPath()` | Type-safe without code |
| 9 | `loan-external-data.feature` | `read()`, `karate.map()`, `karate.filter()` | External data + JS power |
| 10 | `loan-response-headers.feature` | `match header`, `responseTime`, SLA gates | Non-functional testing |
| 11 | `bulk-callback.feature` | `table`, `karate.map()`, `karate.forEach()` | Bulk ops, no loops |
| ✦ | `karate-config.js` | `configure headers`, `callSingle` pattern | Enterprise-grade config |

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

## 2 — Response Body Partial Match + Default Headers
**File:** `src/test/java/karate/features/smoke/basic-api.feature`

```gherkin
Scenario: Read-only customer status endpoint responds
  Given url baseUrl + '/api/customers/smoke/status'
  And headers defaultHeaders
  When method get
  Then status 200
  And match response contains { service: '#string' }
```

**What to say:**
- "`defaultHeaders` is injected from `karate-config.js` — set once, reused everywhere. No per-scenario header repetition."
- "`match response contains` is a partial match — it only checks that `service` exists and is a string. Other fields in the response are ignored."
- "This is the difference between `match ==` (strict, all fields must match) and `match contains` (partial, only listed fields checked)."

---

## 3 — Auth Headers, Negative Testing, `karate.abort()`
**File:** `src/test/java/karate/features/smoke/auth.feature`

**Lines to highlight:**
```gherkin
Scenario: Protected endpoint rejects missing authentication
  Given url baseUrl + '/api/protected/profile'
  When method get
  Then status 401                          # ← negative test — assert failure

Scenario: Protected endpoint accepts configured token
  * if (!authToken) karate.abort()         # ← skip gracefully if no token configured
  And header Authorization = 'Bearer ' + authToken
  Then status 200
```

**What to say:**
- "Negative tests look identical to positive tests — just assert 4xx instead of 2xx."
- "`karate.abort()` skips a scenario cleanly when a precondition is not met. No `@Disabled`, no flaky assumptions."
- "The auth token comes from `karate-config.js` — configured once, all features inherit it."

---

## 4 — REST Callback Smoke Test with Correlation ID
**File:** `src/test/java/karate/features/rest/bank-callback-smoke.feature`

```gherkin
Scenario: Valid bank callback receives a synchronous response
  * def correlationId = 'smoke-' + java.util.UUID.randomUUID()
  Given url baseUrl + '/callbacks/bank/verifications'
  And header x-correlation-id = correlationId
  And request
    """
    {
      "correlationId": "#(correlationId)",
      "customerId": "smoke-bank-customer",
      "accountNumber": "smoke-account",
      "requestType": "CUSTOMER_VERIFICATION"
    }
    """
  When method post
  Then status 200
  And match response.correlationId == correlationId
```

**What to say:**
- "`'smoke-' + java.util.UUID.randomUUID()` — Karate has direct Java interop. No extra UUID library needed."
- "`#(correlationId)` inside the request body is an embedded expression — the variable value is substituted at runtime."
- "The final assertion proves the API echoes the correlation ID back — contract validation in one line."

---

## 5 — Bearer Token Auth + Negative Validation
**File:** `src/test/java/karate/features/rest/salesforce-callback-smoke.feature`

**Lines to highlight:**
```gherkin
Scenario: Valid Salesforce callback is accepted
  And header Authorization = authToken ? 'Bearer ' + authToken : 'Bearer smoke-token'
  Then status 202
  And match response.correlationId == correlationId

@negative
Scenario: Invalid Salesforce callback is rejected
  And request
    """
    {
      "correlationId": "#(correlationId)",
      "customerId": "",      ← empty required field
      "email": "invalid"     ← bad format
    }
    """
  When method post
  Then status 400
```

**What to say:**
- "The ternary expression for the Authorization header — `authToken ? ... : ...` — is valid JS evaluated inline. No helper method needed."
- "The `@negative` tag lets CI filter and run only negative scenarios: `mvn verify -Psmoke -Dkarate.options='--tags @negative'`"
- "Two scenarios — happy path and failure path — in the same feature file. The negative test proves the API rejects bad input, not just that it accepts good input."

---

## 6 — Full E2E Business Journey
**File:** `src/test/java/karate/features/business/digital-loan-origination.feature`

### 6a — Reusable helper via `karate.call()`
```gherkin
* def result = karate.call(
    'classpath:karate/features/business/helpers/submit-loan.feature',
    { baseUrl: baseUrl, correlationId: correlationId, payload: payload }
  )
```
**Say:** "`karate.call()` is like calling a method, but it's a whole feature file. No Spring context, no base class."

### 6b — Exact array membership assertion
```gherkin
* match application.timeline[*].name contains only
  ['LOAN_INITIATED', 'KYC_VALIDATION', 'CREDIT_BUREAU', ...]
```
**Say:** "`contains only` verifies exact membership regardless of order. One line validates the entire business pipeline ran correctly."

### 6c — `retry until` for async
```gherkin
And retry until response.status == 'APPROVED'
When method get
```
**Say:** "Polls until the condition is true, up to the retry count set in `karate-config.js`. No Awaitility, no while-loop, no sleep."

### 6d — Idempotency (same key → same result)
```gherkin
* def first     = submitLoan(... idempotencyKey)
* def duplicate = submitLoan(... idempotencyKey)
* match duplicate.loanId == first.loanId
```
**Say:** "Two API calls, one assertion. Proves the idempotency contract without any server-side mocking."

---

## 7 — Data-Driven Testing (Scenario Outline)
**File:** `src/test/java/karate/features/business/loan-eligibility-data-driven.feature`

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
- "One scenario, five test cases. Each row runs independently with its own name in the HTML report."
- "Numbers in the table (`780`, `110000`) are evaluated as JS numbers — no casting, no type conversion."
- "Add a new borrower profile? Add one row. Zero Java change needed."
- "The `profile` column is the scenario name in the report — readable by a business analyst."

---

## 8 — Schema & Type Validation
**File:** `src/test/java/karate/features/business/loan-schema-validation.feature`

### 8a — Type schema with `match contains`
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
**Say:** "`#string`, `#number`, `#boolean`, `#null`, `#array`, `#object`, `#regex` are built-in type markers. No external JSON Schema library. `match contains` checks only listed fields — additional fields in the response are ignored."

### 8b — `match each` with `#?` predicates
```gherkin
* match each application.timeline contains { name: '#? _.length > 0', outcome: '#string' }
```
**Say:** "`match each` runs the schema against every element in the array. `#? _.length > 0` is an inline JS predicate — `_` represents the field value being tested."

### 8c — Strict vs partial schema (key demo point)
```gherkin
# Strict — every field must be listed
* def timelineItemSchema = { name: '#string', status: '#string', outcome: '#string', reason: '#string' }
* match application.timeline == '#[] timelineItemSchema'

# Partial — extra fields are ignored
* match each application.timeline contains { name: '#string', outcome: '#string' }
```
**Say:** "This is the most important distinction in Karate schema validation. `==` is strict — add a field to the response and the test breaks. `contains` is partial — new fields are ignored. Choose based on whether you own the contract."

### 8d — `karate.jsonPath()`
```gherkin
* def compensationStep = karate.jsonPath(application, "$.timeline[?(@.name == 'COMPENSATION')]")
* assert compensationStep.length == 1
```
**Say:** "Full JsonPath filter expressions — like SQL WHERE on JSON. Extracts only the element matching the condition."

---

## 9 — External Data Files + Array Operations
**File:** `src/test/java/karate/features/business/loan-external-data.feature`
**Data:** `src/test/resources/karate/testdata/borrowers.json`

### 9a — `read()`
```gherkin
* def borrowers = read('classpath:karate/testdata/borrowers.json')
* def profile   = borrowers.profiles.prime
```
**Say:** "`read()` loads JSON, YAML, CSV, or JS files. Test data lives outside the feature file — QA owns the data file, dev owns the test logic. Updating test data never requires a code change."

### 9b — `karate.map()` and `karate.filter()`
```gherkin
* def results  = karate.map(borrowers.bulkSubmissions, mapFn)
* def approved = karate.filter(results, function(r){ return r.status == 'APPROVED' })
* assert approved.length == 2
```
**Say:** "This is Java Stream `.map().filter()` without a single line of Java. The function is defined with triple quotes for multi-line JS, then passed by reference."

---

## 10 — Response Headers + Performance SLA
**File:** `src/test/java/karate/features/business/loan-response-headers.feature`

### 10a — `match header`
```gherkin
And match header Content-Type contains 'application/json'
```
**Say:** "Header name is case-insensitive. `contains` handles charset suffixes and vendor types — `application/json; charset=utf-8` will pass."

### 10b — `responseTime` SLA
```gherkin
* print 'Response time (ms):', responseTime
* assert responseTime < 3000
```
**Say:** "`responseTime` is a built-in Karate variable — milliseconds from request sent to full response received. One line turns a functional test into a performance gate. No external tool needed."

### 10c — Per-feature `configure headers`
```gherkin
* configure headers =
  """
  function() {
    return { 'x-request-id': 'req-' + java.util.UUID.randomUUID() };
  }
  """
```
**Say:** "This overrides the global config just for this feature. The function runs before every HTTP call — each request automatically gets a fresh tracking ID."

---

## 11 — Bulk Operations: `table`, `karate.map()`, `karate.forEach()`
**File:** `src/test/java/karate/features/rest/bulk-callback.feature`

### 11a — `table` keyword
```gherkin
* table customers
  | correlationId  | customerId     | firstName | email                |
  | newId('corr')  | newId('cust')  | 'Alice'   | 'alice@example.test' |
  | newId('corr')  | newId('cust')  | 'Bob'     | 'bob@example.test'   |
```
**Say:** "`table` builds a JSON array from a Gherkin table. Each cell is evaluated as a Karate expression — function calls, variables, and string literals all work in the same table."

### 11b — `match each` on a plain number array
```gherkin
* match each statusCodes == 400
```
**Say:** "After submitting invalid payloads, this single line asserts every response was a 400. No for-loop, no collect, no assertThat."

### 11c — `karate.forEach()` for side effects
```gherkin
* karate.forEach(accepted, function(r){ karate.log('Accepted customerId:', r.customerId) })
```
**Say:** "`forEach` is for side effects — logging, setting state — where you don't need a return value. It keeps `map` and `filter` semantically clean: map transforms, filter selects, forEach acts."

---

## ✦ Enterprise Configuration (`karate-config.js`)
**File:** `src/test/resources/karate-config.js`

### `configure headers` globally
```javascript
karate.configure('headers', function() {
  return { 'x-request-id': 'auto-' + java.util.UUID.randomUUID() };
});
```
**Say:** "This injects a fresh tracking header on every HTTP request across the entire suite automatically. Zero per-scenario setup required."

### `callSingle` pattern
```javascript
// var session = karate.callSingle(
//   'classpath:karate/features/auth/get-token.feature',
//   { baseUrl: baseUrl, authToken: authToken }
// );
// authToken = session.token;
```
**Say:** "`callSingle` runs ONCE per JVM session — not once per scenario, not once per feature, once total. Use it to fetch an OAuth token so you don't hit your auth server 30 times during a full suite run. The result is cached across all parallel threads."

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
| "How do we debug?" | `print` logs to console. `karate.log()` logs with context. HTML report shows every request/response. |
| "Does it generate reports?" | Yes — built-in HTML report with request/response bodies, timings, and pass/fail per scenario. |
| "What is `match ==` vs `match contains`?" | `==` is strict (all fields must match). `contains` is partial (extra fields ignored). Use `contains` when you don't own the full contract. |
| "What are the 4 skipped features in the report?" | Helper features tagged `@ignore` — `submit-loan`, `get-token`, `submit-callback`, `submit-bank-callback`. They are called by other features, not run directly. |
