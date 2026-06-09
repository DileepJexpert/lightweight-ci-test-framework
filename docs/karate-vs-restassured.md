# Karate vs RestAssured — Side-by-Side Comparison

> Reference for cross-questions during demos or architecture reviews.
> Every code block shows the same test written in both frameworks.

---

## 1. A Basic POST + Status + Body Assert

**RestAssured (Java):**
```java
given()
    .contentType(ContentType.JSON)
    .header("x-correlation-id", correlationId)
    .body(Map.of(
        "customerId", "cust-001",
        "creditScore", 780,
        "monthlyIncome", 110000,
        "requestedAmount", 900000,
        "processingMode", "STANDARD"
    ))
.when()
    .post("/api/loans")
.then()
    .statusCode(202)
    .body("status", equalTo("APPROVED"))
    .body("offerId", notNullValue());
```

**Karate:**
```gherkin
Given url baseUrl + '/api/loans'
And header x-correlation-id = correlationId
And request { customerId: 'cust-001', creditScore: 780, monthlyIncome: 110000,
              requestedAmount: 900000, processingMode: 'STANDARD' }
When method post
Then status 202
And match response.status == 'APPROVED'
And match response.offerId == '#string'
```

**Difference:** Karate removes all static imports, `Map.of(...)`, and chained DSL. JSON is written as JSON.

---

## 2. Schema / Type Validation

**RestAssured:**
```java
// Requires an external JSON Schema file + io.rest-assured:json-schema-validator dependency
given().get("/api/loans/" + loanId)
.then()
    .body(matchesJsonSchemaInClasspath("schemas/loan-response-schema.json"));

// OR manually chain matchers per field:
.body("loanId",    matchesPattern("[A-Za-z0-9\\-]+"))
.body("status",    isA(String.class))
.body("timeline",  hasSize(greaterThan(0)))
.body("timeline[0].name", isA(String.class));
```

**Karate:**
```gherkin
* match response contains
  """
  {
    loanId:   '#regex [A-Za-z0-9\\-]+',
    status:   '#string',
    offerId:  '#string',
    timeline: '#[] #object'
  }
  """
* match each response.timeline == { name: '#? _.length > 0', outcome: '#string' }
```

**Difference:**
- Karate's type markers (`#string`, `#number`, `#null`, `#regex`, `#[]`, `#object`, `#?`) are **built-in** — no extra library.
- `match each` validates **every array element** in one line.
- `#? _.length > 0` is an inline predicate — no custom Hamcrest matcher class.

---

## 3. Data-Driven Testing

**RestAssured (JUnit 5 Parameterized):**
```java
@ParameterizedTest(name = "{0} borrower → {5}")
@MethodSource("borrowerProfiles")
void loanDecision(String profile, String mode, int score,
                  int income, int debt, String expected) {
    given()
        .body(Map.of("processingMode", mode, "creditScore", score, ...))
        .post("/api/loans")
    .then()
        .body("status", equalTo(expected));
}

static Stream<Arguments> borrowerProfiles() {
    return Stream.of(
        Arguments.of("prime",    "STANDARD",       780, 110000, 18000, "APPROVED"),
        Arguments.of("subprime", "STANDARD",       650,  45000, 26000, "REJECTED"),
        Arguments.of("timeout",  "CREDIT_TIMEOUT", 775, 100000, 16000, "CREDIT_CHECK_PENDING")
    );
}
```

**Karate:**
```gherkin
Scenario Outline: <profile> borrower receives <expectedStatus>
  * def app = submitLoan('<mode>', <score>, <income>, <debt>, <amount>, newId('idem'))
  * match app.status == '<expectedStatus>'

  Examples:
    | profile    | mode           | score | income | debt  | amount  | expectedStatus       |
    | prime      | STANDARD       | 780   | 110000 | 18000 | 900000  | APPROVED             |
    | subprime   | STANDARD       | 650   | 45000  | 26000 | 1500000 | REJECTED             |
    | timeout    | CREDIT_TIMEOUT | 775   | 100000 | 16000 | 800000  | CREDIT_CHECK_PENDING |
```

**Difference:**
- No `@MethodSource`, no `Stream<Arguments>`, no separate data method.
- The Examples table **is** the documentation — product owners can read and extend it.
- Each row gets its own named test in the HTML report using the `profile` column value.

---

## 4. External Test Data Files

**RestAssured:**
```java
// Requires ObjectMapper + POJO or Map deserialization
ObjectMapper mapper = new ObjectMapper();
Map<String, Object> data = mapper.readValue(
    getClass().getResourceAsStream("/testdata/borrowers.json"),
    new TypeReference<>() {}
);
Map<String, Object> prime = (Map<String, Object>) 
    ((Map<String, Object>) data.get("profiles")).get("prime");
// Now use prime.get("creditScore") etc.
```

**Karate:**
```gherkin
* def borrowers = read('classpath:karate/testdata/borrowers.json')
* def profile   = borrowers.profiles.prime
* def app       = submitProfile(profile, newId('idem'))
```

**Difference:** `read()` handles JSON, YAML, CSV, and JS files natively. No ObjectMapper, no TypeReference, no casting.

---

## 5. Async / Polling

**RestAssured + Awaitility:**
```java
// Requires io.rest-assured + org.awaitility as separate dependencies
Awaitility.await()
    .atMost(10, TimeUnit.SECONDS)
    .pollInterval(250, TimeUnit.MILLISECONDS)
    .until(() -> {
        String status = given().get("/api/loans/" + loanId)
                               .then().extract().path("status");
        return "APPROVED".equals(status);
    });
```

**Karate:**
```gherkin
Given url baseUrl + '/api/loans/' + loanId
And retry until response.status == 'APPROVED'
When method get
Then status 200
```

**Difference:** `retry until` is a single keyword. Retry count and interval are configured once globally in `karate-config.js`. No extra library.

---

## 6. Reusable Request Flows

**RestAssured:**
```java
// Requires a base class or utility class with a static method
public class LoanTestHelper {
    public static String submitLoan(RequestSpecification spec,
                                    String mode, int score, ...) {
        return spec
            .body(Map.of("processingMode", mode, "creditScore", score, ...))
            .post("/api/loans")
            .then().statusCode(202)
            .extract().path("loanId");
    }
}
// Every test class must extend or import LoanTestHelper
```

**Karate:**
```gherkin
# submit-loan.feature (@ignore)
Given url baseUrl + '/api/loans'
And request payload
When method post
Then status 202

# Any other feature:
* def result = karate.call('classpath:.../submit-loan.feature', { payload: payload })
```

**Difference:** No base class, no inheritance, no static imports. The helper is a `.feature` file — it works across features, environments, and parallel threads without shared mutable state.

---

## 7. Response Header Assertions

**RestAssured:**
```java
given().get("/api/loans/" + loanId)
.then()
    .header("Content-Type", containsString("application/json"))
    .header("x-correlation-id", equalTo(correlationId));
```

**Karate:**
```gherkin
When method get
Then status 200
And match header Content-Type contains 'application/json'
```

**Difference:** Functionally identical. Karate is marginally shorter; RestAssured's Hamcrest matchers are more expressive for complex patterns.

---

## 8. Performance / SLA Assertions

**RestAssured:**
```java
// No built-in responseTime — must measure manually
long start = System.currentTimeMillis();
given().get("/api/loans/" + loanId).then().statusCode(200);
long elapsed = System.currentTimeMillis() - start;
assertThat(elapsed).isLessThan(1000L);
```

**Karate:**
```gherkin
When method get
Then status 200
* assert responseTime < 1000
```

**Difference:** `responseTime` is a **built-in Karate variable**. RestAssured has no equivalent — you time it manually.

---

## 9. Bulk Operations

**RestAssured:**
```java
List<String> customerIds = List.of("cust-001", "cust-002", "cust-003");
List<Integer> statusCodes = customerIds.stream()
    .map(id -> given()
            .body(Map.of("customerId", id, "email", id + "@test.com", ...))
            .post("/callbacks/salesforce/customers")
            .statusCode())
    .collect(Collectors.toList());

assertThat(statusCodes).allMatch(code -> code == 202);
```

**Karate:**
```gherkin
* table customers
  | customerId  | email                    |
  | 'cust-001'  | 'cust-001@example.test'  |
  | 'cust-002'  | 'cust-002@example.test'  |

* def results = karate.map(customers, function(c){
    var r = karate.call('classpath:.../submit-callback.feature', { customer: c });
    return r.statusCode
  })
* match each results == 202
```

**Difference:** `table` builds the array. `karate.map()` replaces the stream. `match each` replaces `assertThat(...).allMatch(...)`.

---

## 10. Environment Switching

**RestAssured:**
```java
// Typically wired through Spring profiles or a custom config loader
@Value("${service.base-url}")
private String baseUrl;
```
Requires Spring context, `@SpringBootTest`, or a custom properties loader. Switching environments means switching Spring profiles or re-running with different `-D` flags **plus** ensuring the test class picks them up.

**Karate:**
```bash
# No code change — same feature files, different property
mvn verify -Psmoke -Dservice.base-url=https://qa.mybank.com   -Dkarate.env=qa
mvn verify -Psmoke -Dservice.base-url=https://uat.mybank.com  -Dkarate.env=uat
mvn verify -Psmoke -Dservice.base-url=http://localhost:8080   -Dkarate.env=local
```

---

## Summary Table

| Capability | Karate | RestAssured |
|---|---|---|
| Language for tests | Gherkin + inline JS | Java |
| Non-developer readable | Yes | No |
| Schema / type validation | Built-in (`#string`, `#regex`, `#?`) | External library or manual matchers |
| Data-driven (tabular) | `Scenario Outline` + `Examples` | `@ParameterizedTest` + `@MethodSource` |
| External data files | `read()` — JSON, YAML, CSV | ObjectMapper + manual deserialization |
| Async / polling | `retry until` (one line) | Awaitility (separate library) |
| Reusable flows | `karate.call()` to a feature file | Static methods / base class |
| Response header assert | `match header` | `.header()` with Hamcrest |
| Performance / SLA | `responseTime` built-in | Manual `System.currentTimeMillis()` |
| Bulk array operations | `table`, `karate.map()`, `karate.filter()` | Java streams |
| Environment switching | `karate-config.js` + `-D` flags | Spring profiles / custom config |
| Parallel execution | `Runner.parallel()` built-in | No built-in support |
| HTML report | Built-in (Karate HTML report) | Requires Allure / Surefire plugin |
| Spring Boot integration | Not needed (calls real HTTP) | Native with `@SpringBootTest` |
| Component / unit tests | Not suited | Native (MockMvc + RestAssured) |
| Setup cost per new test | 0 Java lines | 1+ Java class + imports |

---

## When to Use Which

### Use Karate when:
- Testing **deployed APIs** (QA, smoke, E2E, regression)
- Non-developers (QA, BA) need to **write or review** tests
- You need **data-driven** tests without Java boilerplate
- You want **schema validation** without an external library
- You're doing **multi-step business journey** tests (submit → poll → review)
- CI/CD with **no Docker** or embedded server required

### Use RestAssured when:
- You need `@SpringBootTest` + **MockMvc** (no running server, component layer)
- Tests are **developer-only** and Java fluency is universal in the team
- Complex **auth flows** (OAuth PKCE, certificate auth) need full Java libraries
- You're testing at the **service/unit level** alongside Mockito

### The practical answer for this project:
**Component tests → Mockito + MockMvc (no RestAssured needed).**
**Smoke / E2E tests → Karate (this repo).**
RestAssured would be a third tool that duplicates what Karate already does better at the API layer.

---

## Tough Cross-Questions — Direct Answers

**Q: RestAssured is more mature and widely used. Why change?**
A: RestAssured is a library; Karate is a framework. Karate handles the full test lifecycle (config, data, HTTP, assertions, retry, reporting) in one tool. RestAssured only handles assertions — you still need JUnit, Awaitility, ObjectMapper, and Allure on top of it.

**Q: Our team only knows Java. Karate's JS is a new language to learn.**
A: The JS in Karate is optional. 80% of tests use only Gherkin keywords (`Given`, `When`, `Then`, `match`, `def`). The JS functions (`karate.map`, `karate.filter`) are only used for bulk operations and can be skipped initially.

**Q: Can Karate test non-JSON APIs?**
A: Yes. Karate has native XML support with XPath matching, plain-text support with `text`, and binary support with `bytes`. SOAP services work out of the box.

**Q: What about mocking dependencies — Karate can't do that, right?**
A: Karate has a mock server capability (`karate-netty`) that can stub upstream services. For component-level mocking, Mockito is still the right choice — the two tools complement each other rather than compete.

**Q: How does Karate handle large test suites — is it slow?**
A: `Runner.parallel(threads)` in the runner class parallelises across features and scenarios automatically. Karate's HTML report merges results from all threads. Typical suites of 100+ scenarios run in under 2 minutes with 4–8 threads.

**Q: RestAssured integrates with Spring Security test context. Karate can't do that.**
A: Correct — and it shouldn't. Spring Security integration tests belong at the component layer (Mockito + MockMvc), not at the API smoke layer. Karate tests the deployed service the same way a real client does — via real HTTP with a Bearer token, which is a stronger test than a mocked security context.
