# Karate Framework — Cross-Questions & Answers

> Prepared for demo / architecture review sessions.
> Every question below has been asked in real teams. Read this once before presenting.

---

## SECTION 1 — "Why Karate at all?"

---

**Q: We already have JUnit. Why do we need Karate?**

A: JUnit is a test runner, not an API testing tool. It cannot make an HTTP call on its own.
If your team uses JUnit for API testing today, they are also using RestAssured, Awaitility,
Jackson ObjectMapper, and an Allure/Surefire plugin on top of it.
Karate replaces all five of those with one framework — and JUnit still runs Karate.
`KarateSmokeTestRunner.java` in this project is a JUnit class. They are not competitors.
The real question is: do you write API tests in Java or in Gherkin?

---

**Q: We have a QA team. They already test. Why automate this separately?**

A: QA testing manually and automated API regression are two different things:

- Manual QA catches exploratory, edge-case, and UX issues.
- Karate smoke tests run in 3 minutes on every deployment, catching regressions automatically.

The bigger point: Karate is the first API test tool where QA can write the automated tests
themselves — no Java required. Without Karate, a developer has to translate QA's test cases
into Java, which is slow and lossy. With Karate, QA writes `.feature` files directly and
they run in CI automatically.

---

**Q: Our QA team uses Postman. That's already automated enough.**

A: Postman and Karate feel similar to write, but operationally they are very different:

| Postman | Karate |
|---|---|
| Stored in Postman cloud | Stored in Git alongside the code |
| Runs manually or with Newman CLI separately | Runs as part of `mvn verify` in CI |
| No retry/polling for async APIs | `retry until` built in |
| Hard to do data-driven parameterisation | `Scenario Outline` + `Examples` table |
| No reusable sub-flows | `karate.call()` for shared flows |
| Test report needs extra tooling | HTML report built in |
| Dies when the QA person leaves | Lives in the repo forever |

Karate is effectively "Postman that lives in your codebase and runs automatically."
Most QA engineers find the switch takes less than a day.

---

**Q: This is just Cucumber with HTTP. We tried Cucumber and it was too much overhead.**

A: Cucumber overhead comes from the step definition layer — every Gherkin line needs a
matching Java method annotated with `@Given`, `@When`, `@Then`. That glue code is the pain.

Karate has no step definitions. The Gherkin keywords map directly to HTTP actions built
into the framework. There is zero glue code to write or maintain. The comparison is:

- Cucumber: Gherkin + Java glue code + assertion library + HTTP client = 4 things to maintain
- Karate: Gherkin = 1 thing to maintain

---

**Q: Why not just use Selenium / Playwright for E2E testing?**

A: Selenium and Playwright test the UI through a browser. Karate tests the API directly.

- Browser tests are 10-50x slower than API tests.
- Browser tests break on UI changes (button moved, CSS class renamed) even when the business logic is correct.
- API tests are stable — the contract changes far less often than the UI.
- For a loan origination backend, the API is the real interface. Test where the logic lives.

---

## SECTION 2 — "Karate vs JUnit / RestAssured specifically"

---

**Q: RestAssured is mature, widely used, and our developers already know it.**

A: RestAssured is a library for HTTP assertions. Karate is a complete framework.
With RestAssured you still need: JUnit, Awaitility (polling), ObjectMapper (data files),
Allure or Surefire (reporting), and a base class pattern for shared flows.

The maturity argument works both ways — Karate 1.x has been production-stable since 2022
and is used at companies like Intuit, eBay, and Morgan Stanley for large-scale API testing.

The adoption argument: RestAssured tests can only be written by Java developers.
Karate tests can be written by QA engineers, BAs, or anyone who can read English.
That is a permanent team productivity difference, not a learning curve.

---

**Q: Our developers are comfortable in Java. Karate's JavaScript is a new language to learn.**

A: 80% of Karate tests use zero JavaScript — only Gherkin keywords:
`Given`, `When`, `Then`, `And`, `match`, `def`, `call`, `read`.
These are not Java and not JavaScript. They are Karate's own DSL, and most developers
learn them in a few hours.

The JavaScript functions (`karate.map`, `karate.filter`, inline predicates) are only needed
for bulk operations and advanced scenarios. A team can start without them and adopt
them progressively.

---

**Q: Can Karate test things other than REST JSON APIs?**

A: Yes:
- **XML / SOAP** — native XML support with XPath matching
- **GraphQL** — it is HTTP POST with a JSON body, works out of the box
- **gRPC** — via HTTP transcoding or the karate-grpc community extension
- **Multipart / file upload** — `multipart file` keyword built in
- **Plain text responses** — `text` keyword
- **Binary responses** — `bytes` keyword
- **WebSocket** — via `karate.async()` pattern (advanced use case)

---

**Q: What about mocking? Karate can't mock dependencies like Mockito can.**

A: Correct, and that is intentional. Karate sits at a different layer:

- **Mockito** mocks Java objects inside a single JVM — used in unit/component tests.
- **Karate** calls a real running service over HTTP — no mocking needed at this layer.

For mocking upstream services that your API depends on (e.g., a credit bureau),
Karate has a built-in mock server (`karate-netty`) that can stub HTTP endpoints.
For component-level mocking, Mockito is still the right tool — they complement each other.

---

**Q: We use Spring Boot. Karate doesn't integrate with Spring context, so it can't test
security, filters, interceptors properly.**

A: Karate tests the deployed service exactly as a real client would — through real HTTP.
That means Spring Security, filters, interceptors, and middleware are all exercised
naturally. There is no mocked security context — the request goes through the full stack.

`@SpringBootTest` with MockMvc is appropriate for component tests that need to inspect
internal Spring wiring. Karate smoke tests validate the complete request path from outside.
Both layers are valuable; neither replaces the other.

---

**Q: How does Karate handle OAuth 2.0 / token-based authentication?**

A: Two patterns depending on the need:

**Pattern 1 — `callSingle` (token fetched once per suite):**
```javascript
// karate-config.js
var session = karate.callSingle(
  'classpath:karate/features/auth/get-token.feature',
  { baseUrl: baseUrl }
);
authToken = session.token;
```
The token endpoint is called once for the entire test run, regardless of how many
scenarios or parallel threads exist. The result is cached.

**Pattern 2 — `configure headers` (token refreshed per request):**
```javascript
karate.configure('headers', function() {
  return { 'Authorization': 'Bearer ' + currentToken };
});
```
If the token has a short TTL, the function recalculates it before every HTTP call.

---

**Q: How does Karate handle parallel test execution?**

A: Built in to the runner — change one number:
```java
Results results = Runner.path("classpath:karate/features")
    .parallel(8);  // 8 threads
```
Karate manages thread safety automatically. Each thread gets its own variable scope.
The HTML report merges results from all threads into a single coherent report.
There is no special test design needed — features are isolated by default.

---

**Q: What does the test report look like? Stakeholders won't understand JUnit XML.**

A: Karate generates a standalone HTML report with:
- Pass/fail status per feature and per scenario
- The full HTTP request (URL, headers, body) for every step
- The full HTTP response (status, headers, body) for every step
- Response time per request
- Colour-coded diff when a `match` assertion fails

No additional plugin (Allure, Extent Reports) is needed. A business stakeholder can open
the HTML file and read what the test did and why it failed — including the exact JSON
that did not match the expected value.

---

## SECTION 3 — "Our specific situation"

---

**Q: We are a small team. We don't have bandwidth to maintain two test frameworks.**

A: You already have two layers if you have JUnit component tests:
1. JUnit + Mockito for component/unit tests (tests Java classes, no HTTP)
2. Something for API smoke tests (manual, Postman, or nothing)

Karate replaces the second layer — it does not add to it. If you have no API smoke tests
today, Karate gives you that layer with the lowest maintenance cost of any tool available,
because non-developers can own and extend the `.feature` files.

---

**Q: How long does it take to set up Karate in a new project?**

A: Three steps:
1. Add the `karate-junit5` dependency to `pom.xml` — 5 minutes
2. Create `karate-config.js` with the base URL — 10 minutes
3. Write the first `.feature` file — 15 minutes

Most teams have their first test running in under an hour. The `KarateSmokeTestRunner.java`
in this project is 11 lines including the package declaration.

---

**Q: What happens when the API changes? Won't all Karate tests break?**

A: The same thing happens with RestAssured tests — any test tied to a contract breaks
when the contract changes. That is the point. A broken test is the early warning system.

The difference with Karate: because test data lives in `borrowers.json` and reusable flows
live in helper `.feature` files, a contract change typically requires updating one
shared file rather than every test class. The `submitLoan` helper is called by five
different scenarios — update it once, all five are fixed.

---

**Q: How do we handle test data setup? Karate can't seed a database.**

A: Karate tests the API, so test data is set up through the API — the same way a real
client would. In this project, the loan application itself is the test data: each scenario
submits a fresh loan with a unique ID and tests the result.

For cases where pre-existing data is required (e.g., a customer record that must already
exist before the test runs), the `Background` section calls the relevant API to create it
before the scenario executes. No direct database access needed, which also means the tests
work against any environment — local, QA, UAT — without environment-specific data scripts.

---

**Q: Karate tests require a running service. Our JUnit tests don't. That makes CI harder.**

A: Karate smoke tests belong to the post-deployment stage of CI, not the build stage:

```
Build stage:    mvn clean test -Pcomponent   ← JUnit + Mockito, no running service
Deploy stage:   deploy to QA environment
Smoke stage:    mvn verify -Psmoke           ← Karate, requires running service
```

This is intentional. Smoke tests validate the deployed environment, not the compiled code.
They catch configuration errors, network policies, certificate issues, and environment
drift that unit tests cannot detect. The Maven profiles in this project (`-Pcomponent`,
`-Psmoke`) make the split explicit and clean.

---

**Q: If the service is down, Karate tests fail. That's noisy — we'll get alert fatigue.**

A: A failing smoke test against a down service is a correct failure, not noise.
The appropriate response is to alert on deployment failures, not to suppress the test.

For genuine flakiness (timing issues, rate limits), Karate has `retry until` for async
scenarios and the global retry configuration in `karate-config.js`. Most spurious failures
come from tests asserting specific values that change — solved by using fuzzy matchers
(`#string`, `#regex`) for dynamic fields and exact matches only for stable business values.

---

## SECTION 4 — "Management / Process questions"

---

**Q: How does this fit into our Definition of Done?**

A: Suggested addition to DoD:
> "A Karate smoke test exists for every new API endpoint, is committed alongside the code,
> and passes in the CI smoke stage before the story is marked Done."

This means QA writes the `.feature` file as part of the same sprint, not in a separate
testing phase. The test is the acceptance criterion in executable form.

---

**Q: Who owns the Karate tests — Dev or QA?**

A: Both, at different stages:
- **Dev** creates the initial `.feature` file as part of the story (same as writing a unit test)
- **QA** extends and maintains it — adds edge cases, negative scenarios, new Examples rows
- **Both** review changes to `.feature` files in the pull request

The `.feature` files are plain text in Git — the same review and ownership process that
applies to code applies to tests. No special tooling or access required.

---

**Q: Can we show test results to the business / management?**

A: Yes, and better than any Java test framework:

- The HTML report shows scenario names written in plain English
  (e.g., "Prime borrower receives APPROVED decision")
- The `digital-loan-origination.feature` file itself is readable as a business specification
- Scenario names in the `Examples` table (e.g., `prime`, `subprime`, `borderline`) appear
  directly in the report — no decoding of method names like `testLoanDecision_3()`

Many teams share the Karate HTML report with product owners after each release
as a "here is what we verified" document.

---

**Q: What is the ROI? How do we justify the time investment?**

A: Frame it as replacing manual regression time, not adding new work:

| Activity | Manual | With Karate |
|---|---|---|
| Regression before each release | 2–3 days QA time | 3–5 minutes CI run |
| Catching a regression introduced by a hotfix | Found in manual QA, days later | Found in CI within minutes of the push |
| Onboarding a new QA engineer to the test suite | Read Postman collections, no context | Read `.feature` files — self-documenting |
| Environment health check after deployment | QA manually checks endpoints | `mvn verify -Psmoke` in the pipeline |

The break-even point is typically 2–3 releases. After that, every regression caught
automatically is time saved and a production incident avoided.

---

## SECTION 5 — Quick one-liners for rapid-fire questions

| Question | One-line answer |
|---|---|
| "Is Karate open source?" | Yes — Apache 2.0 licence, maintained by Peter Thomas and the community. |
| "Does it work with Maven and Gradle?" | Yes, both. This project uses Maven. |
| "Can it run in Jenkins / GitHub Actions / GitLab CI?" | Yes — it is `mvn verify`. Any CI that runs Maven runs Karate. |
| "Does it support HTTPS / SSL?" | Yes — `configure ssl = true` or configure a custom truststore. |
| "Can it test behind a VPN or on-premise?" | Yes — run it from a machine inside the network. No external service needed. |
| "What Java version is needed?" | Java 11 minimum. This project uses Java 21. |
| "Is there IDE support?" | IntelliJ has a Karate plugin with syntax highlighting and step navigation. |
| "Can it generate Allure reports?" | Yes — Karate has an Allure adapter. The built-in HTML report is usually sufficient. |
| "Can it integrate with Jira for test management?" | Via Xray or Zephyr plugins that consume JUnit XML output from Karate. |
| "Does it support cookies and sessions?" | Yes — `cookie` keyword, and cookies are automatically maintained across requests in a scenario. |
| "Can it handle file uploads?" | Yes — `multipart file` keyword. |
| "What about rate limiting — will bulk tests hit limits?" | Use `karate.pause(ms)` between calls or reduce `parallel` threads. |
| "Can it test GraphQL?" | Yes — GraphQL is a POST with a JSON body. Works out of the box. |
| "Can it test SOAP / XML?" | Yes — native XML support with XPath assertions. |
