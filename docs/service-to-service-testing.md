# Testing service-to-service calls (Service A → Service B) in EKS

## The scenario

Multiple services are deployed in the same EKS cluster. You write a Karate test for an
endpoint on **Service A**, but that endpoint internally calls **Service B** (for example a
KYC service) using `RestClient` / `WebClient`. How does the test behave?

```
Karate ──HTTP──► Service A (/callbacks/bank/verifications)
                     │
                     │ RestClient (in-cluster)
                     ▼
                 Service B (http://kyc-service.<ns>.svc.cluster.local:8080/kyc/validate)
                     │
                     ▼
                 result ──► Service A ──► response back to Karate
```

## How it behaves

Karate is a **black-box HTTP client**. It only talks to Service A and never sees Service B.
When Service A receives the request it makes a real downstream call, so a single Karate POST
exercises a true **end-to-end A → B → A integration**.

In EKS the downstream URL is the **Kubernetes internal Service DNS** name
(`kyc-service.<namespace>.svc.cluster.local`), resolved by CoreDNS and routed to a healthy
Service B pod. No mocking happens at the network layer — this is real in-cluster traffic.

### The catch

Service B must be **deployed and healthy** in the same namespace, or the test fails for
reasons unrelated to Service A. If Service B is down or returns 5xx, `RestClient` throws a
`RestClientException`, which `CustomerVerificationService` already catches and maps to
`PENDING_RETRY` — so the failure degrades gracefully rather than crashing.

## How this framework wires it

The downstream call sits behind the `ThirdPartyKycClient` interface (the seam):

| Environment | Bean used | Activated by | Behaviour |
|---|---|---|---|
| Local demo | in-memory stub (`LocalDemoConfiguration`) | `kyc.service.base-url` **absent** | always returns `VERIFIED`, no Service B needed |
| QA / SIT / EKS | `RestClientKycClient` (`ServiceClientConfiguration`) | `kyc.service.base-url` **set** | real HTTP call to Service B |
| Component tests | Mockito mock / `MockRestServiceServer` | n/a | no network at all |

The two beans key off the same property and are mutually exclusive, so there is never a
bean conflict regardless of configuration order.

### Running against a real Service B (QA/SIT/EKS)

```bash
java -jar app.jar \
  --kyc.service.base-url=http://kyc-service.loan-namespace.svc.cluster.local:8080

mvn verify -Psmoke \
  -Dservice.base-url=https://loan-api.internal.company.com \
  -Dkarate.options="classpath:karate/features/rest/service-to-service-kyc.feature"
```

### Running locally (no Service B)

```bash
mvn spring-boot:run          # stub mode — no kyc.service.base-url
mvn verify -Psmoke -Dservice.base-url=http://localhost:8080
```

## Testing Service A in isolation

If you want to test Service A without deploying the real Service B, point the
`kyc-service` Kubernetes `Service` at a WireMock/stub pod, or start Service A without
`kyc.service.base-url` so it uses the in-process stub. Service A is unaware of the
difference.

## Relevant files

- `src/main/java/com/example/lightweight/client/RestClientKycClient.java` — real downstream client
- `src/main/java/com/example/lightweight/config/ServiceClientConfiguration.java` — activates it when configured
- `src/main/java/com/example/lightweight/config/LocalDemoConfiguration.java` — stub fallback
- `src/test/java/component/client/RestClientKycClientTest.java` — in-process verification (no Docker)
- `src/test/java/karate/features/rest/service-to-service-kyc.feature` — black-box A → B smoke test
