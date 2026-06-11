# Karate Production Safety — Environment Strategy Guide

> **The most important rule before going live with Karate in a pipeline:**
> Karate does not know whether the environment is production or QA.
> It only calls the URL you configure. If that URL is production and the API writes data,
> production data will be affected.

---

## The Core Risk

```
POST /api/loans       ← creates a real loan record in prod DB
POST /api/customers   ← creates a real customer in CRM
POST /callbacks/bank  ← triggers a real bank verification
POST /api/payments    ← initiates a real payment transaction
```

If your Karate suite runs these against a production URL, it will:
- Insert rows into the production database
- Trigger downstream Kafka events to real consumers
- Send real SMS / email notifications to real customers
- Call real third-party APIs (KYC, credit bureau, payment gateways)
- Create audit trail entries that compliance teams will see
- Potentially charge real money

---

## What Karate CAN Safely Run in Production

Production Karate tests must be **read-only and stateless**:

```gherkin
# SAFE in production
GET  /actuator/health          ← no data change
GET  /actuator/info            ← no data change
GET  /actuator/readiness       ← no data change
GET  /version                  ← no data change
GET  /api/config-status        ← no data change (if available)
GET  /api/loans/{known-id}     ← read existing data, no write
     auth token validation     ← only checks 401/200, no write
     TLS / SSL check           ← connectivity only
     response time assertion   ← no data change
```

```gherkin
# NEVER in production
POST /api/loans                ← creates real business record
POST /api/customers            ← creates real customer
POST /callbacks/salesforce     ← triggers real downstream event
PUT  /api/loans/{id}/review    ← modifies real loan decision
DELETE /api/records            ← destructive
```

---

## Test Strategy by Environment

| Environment | Karate Suite | Write APIs Allowed | Purpose |
|---|---|---|---|
| Local | Full suite | ✅ Yes | Developer testing |
| Dev | Full suite | ✅ Yes | Integration testing |
| QA / SIT | Full regression including write, Kafka, DB | ✅ Yes, with test data markers | Full contract verification |
| UAT | Limited business validation | ⚠️ Controlled, with approval | Acceptance testing |
| **Production** | **Read-only smoke only** | **❌ No business writes** | **Deployment health check** |

---

## How to Implement the Guardrails

### 1. Separate Feature Folders by Safety Level

```
src/test/java/karate/features/
├── smoke/                    ← always safe, run everywhere
│   ├── health.feature
│   ├── auth.feature
│   ├── basic-api.feature
│   └── readiness.feature
│
├── prod-smoke/               ← production-only suite (read-only)
│   ├── health.feature
│   ├── gateway-routing.feature
│   ├── auth-readonly.feature
│   └── version.feature
│
├── business/                 ← write tests — QA/SIT only
│   ├── digital-loan-origination.feature
│   ├── loan-eligibility-data-driven.feature
│   └── ...
│
└── rest/                     ← write tests — QA/SIT only
    ├── bulk-callback.feature
    └── salesforce-callback-smoke.feature
```

### 2. Tag Every Feature Correctly

```gherkin
# Safe for all environments including production
@smoke @prod-safe @health
Feature: Deployed service health smoke checks

# QA/SIT only — writes data
@smoke @business @non-prod-only @write
Feature: Digital loan origination management journey

# QA/SIT only — creates callbacks
@smoke @rest @non-prod-only @write
Feature: Bulk Salesforce callback processing
```

Tag reference:

| Tag | Meaning | Runs in Prod? |
|---|---|---|
| `@prod-safe` | Read-only, stateless | ✅ Yes |
| `@smoke` | Basic connectivity check | ✅ Yes (filter with `@prod-safe`) |
| `@write` | Creates / modifies data | ❌ No |
| `@non-prod-only` | Explicitly blocked from prod | ❌ No |
| `@business` | Full business journey | ❌ No |
| `@kafka` | Publishes Kafka events | ❌ No |
| `@e2e` | End-to-end with side effects | ❌ No |
| `@negative` | 4xx validation (usually safe) | ⚠️ Check per case |

### 3. GoCD Pipeline Uses Different Tag Filters per Environment

```yaml
# QA pipeline — full regression
- name: Run Karate QA
  tasks:
    - exec:
        command: mvn
        arguments:
          - verify
          - -Psmoke
          - -Dservice.base-url=${QA_SERVICE_URL}
          - -Dkarate.options=--tags @smoke

# Production pipeline — read-only only
- name: Run Karate Prod
  tasks:
    - exec:
        command: mvn
        arguments:
          - verify
          - -Psmoke
          - -Dservice.base-url=${PROD_SERVICE_URL}
          - -Dkarate.options=--tags @prod-safe   # ← NEVER @write or @business
```

### 4. Separate karate-config.js Behaviour by Environment

```javascript
// karate-config.js
function fn() {
  var env     = karate.env || 'qa';
  var baseUrl = karate.properties['service.base-url'] || 'http://localhost:8080';

  // Production guard — warn loudly if write-capable features are attempted
  if (env === 'production') {
    karate.log('WARNING: Running in PRODUCTION — only @prod-safe tests should execute');
    karate.configure('readTimeout', 5000);   // tighter timeout in prod
    karate.configure('connectTimeout', 3000);
  }

  return {
    env:       env,
    baseUrl:   baseUrl,
    authToken: karate.properties['auth.token'] || java.lang.System.getenv('AUTH_TOKEN') || 'local-token',
    isProd:    env === 'production'   // feature files can check this flag
  };
}
```

In a feature file, you can then abort if a write scenario is accidentally triggered in prod:

```gherkin
@write @non-prod-only
Feature: Digital loan origination management journey

  Background:
    # Hard stop if this feature is run against production
    * if (isProd) karate.fail('Write tests must not run in production. Use @prod-safe features only.')
```

### 5. Test Data Markers in Non-Prod Write Requests

For QA/SIT where write APIs are used, mark every payload as test data:

```gherkin
And request
  """
  {
    "correlationId":   "#(correlationId)",
    "customerId":      "karate-test-#(java.util.UUID.randomUUID())",
    "source":          "KARATE_AUTOMATION",
    "isTestData":      true,
    "requestedAmount": 900000
  }
  """
```

Your QA database cleanup job can then purge rows where `source = 'KARATE_AUTOMATION'` on a schedule.

### 6. API-Level Guard (Recommended for Production APIs)

The safest design: production services reject requests that carry automation headers:

```
Request header:  X-Automation-Test: true
```

Production service responds: `403 Forbidden — automation headers not accepted in production`

This means even if someone accidentally points Karate at production, the service protects itself.

---

## Production Smoke Feature Files (in this repo)

Four feature files are in `src/test/java/karate/features/prod-smoke/`:

| Feature File | What It Validates | Scenarios |
|---|---|---|
| `health-readiness.feature` | `/actuator/health`, liveness probe, readiness probe, SLA | 3 |
| `auth-validation.feature` | 401 without token, 200 with valid token, malformed token | 3 |
| `api-connectivity.feature` | Ingress routing, 404 proves route works, Content-Type header | 3 |
| `performance-sla.feature` | Response time gates: health 500ms, status 1000ms, auth 1500ms | 4 |

All 13 scenarios are `@prod-safe` — zero writes, zero data created.

Run them against any environment:
```bash
# Against production — dedicated profile, runs ONLY KarateProdSmokeRunner
mvn verify -Pprod-smoke \
  -Dservice.base-url=https://loan-api.company.com \
  -Dkarate.env=production \
  -Dauth.token=${EKS_AUTH_TOKEN}

# Against local service to verify they work first
mvn verify -Pprod-smoke

# Alternative: tag filter through the general smoke profile
mvn verify -Psmoke -Dkarate.options="--tags @prod-safe"
```

Prefer `-Pprod-smoke` for the production pipeline stage: it executes only
`KarateProdSmokeRunner.java`, which is hard-wired to the `prod-smoke/` folder and the
`@prod-safe` tag. Even a wrong `-Dkarate.options` tag filter cannot pull in write
scenarios, because the runner never looks outside `prod-smoke/`.

---

## Corrected One-Liner for Management / Demo

> **JUnit protects the build. Karate protects the release.
> In QA and SIT, Karate validates the full API contract including write operations.
> In production, Karate is strictly read-only — proving the deployment is healthy
> without touching any business data.**

Or in one sentence:

> **Unit tests prove the code is correct. Karate proves the deployment is correct.
> In production, Karate must be read-only.**

---

## Summary Checklist Before Running Karate in Production

- [ ] Only `@prod-safe` tagged features are in the production pipeline tag filter
- [ ] No `POST`, `PUT`, `DELETE` scenarios in the prod-smoke folder
- [ ] `karate-config.js` sets `isProd = true` for production environment
- [ ] Write features have `@non-prod-only` tag AND a `karate.fail()` guard for prod env
- [ ] GoCD production stage uses `-Dkarate.options=--tags @prod-safe`
- [ ] Test data markers (`source=KARATE_AUTOMATION`) used in all QA write payloads
- [ ] QA database has a cleanup job for `KARATE_AUTOMATION` records
- [ ] Team agrees: production Karate = health check, not regression test
