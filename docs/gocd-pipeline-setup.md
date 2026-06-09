# Running Karate Smoke Tests in GoCD Pipeline

> GoCD by ThoughtWorks — Pipeline as Code guide for the Karate smoke test suite.
> Covers: pipeline stages, YAML config, environment variables, EKS health check, and troubleshooting.

---

## Why Run Karate AFTER Deployment? (The Most Common Question)

> **"If we already have unit tests, what does Karate add after the pod is up?"**

Unit/component tests prove your **code** is correct.
Karate smoke tests prove your **deployment** is correct.
These are two completely different failure modes.

| What Gets Tested | Unit / Component Tests (Stage 1) | Karate Smoke Tests (Stage 4) |
|---|---|---|
| Business logic correct | ✅ | ✅ (via real API call) |
| Code compiles | ✅ | — |
| Validation rules work | ✅ | ✅ (negative scenarios) |
| Mocked dependencies pass | ✅ | — |
| **Pod actually started in EKS** | ❌ | ✅ |
| **ConfigMap / Secrets mounted correctly** | ❌ | ✅ |
| **Database connection string correct** | ❌ | ✅ |
| **External service URLs reachable from pod** | ❌ | ✅ |
| **Ingress / Load balancer routing works** | ❌ | ✅ |
| **SSL certificate valid** | ❌ | ✅ |
| **Auth token accepted by live service** | ❌ | ✅ |
| **Real response times within SLA** | ❌ | ✅ |
| **Environment variable injected correctly** | ❌ | ✅ |

**Real example of what only Karate catches:**
- All 200 unit tests pass ✅
- Pod deploys to EKS ✅
- Pod crashes on startup because `DB_PASSWORD` secret was not mounted → Karate `POST /api/loans` returns 500 → pipeline fails → team alerted within 3 minutes → production never affected

> A correct codebase deployed incorrectly is still an outage.
> Unit tests cannot catch deployment failures. Karate can.

---

## How It Fits in the Delivery Pipeline

```
Git Push
   │
   ▼
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   STAGE 1        │────▶│   STAGE 2        │────▶│   STAGE 3        │────▶│   STAGE 4        │
│  Build & Test    │     │  Package & Push  │     │  Deploy to EKS   │     │  Karate Smoke    │
│                  │     │                  │     │                  │     │                  │
│ mvn test         │     │ docker build     │     │ kubectl apply    │     │ mvn verify       │
│ -Pcomponent      │     │ docker push      │     │ kubectl rollout  │     │ -Psmoke          │
│                  │     │                  │     │ status (wait)    │     │                  │
│ No server needed │     │ ECR / registry   │     │ Pod must be 1/1  │     │ Hits real EKS URL│
└──────────────────┘     └──────────────────┘     └──────────────────┘     └──────────────────┘
      Fails fast               Artifact                Gate: pod             Full API contract
      on code issues           published               healthy before        verified on live
                                                       smoke runs            environment
```

**Key rule:** Stage 4 (Karate) only starts if Stage 3 confirms the pod is `Running` and `Ready`.
If the pod never becomes healthy, GoCD marks Stage 3 as failed and Stage 4 never runs.

---

## GoCD Pipeline as Code (YAML)

Place this file at the root of your repository: `.gocd/loan-service-pipeline.gocd.yaml`

> Requires the **gocd-yaml-config-plugin** installed on your GoCD server.
> Plugin page: https://github.com/tomzo/gocd-yaml-config-plugin

```yaml
format_version: 10

pipelines:
  loan-service-delivery:
    group: loan-applications
    label_template: "${git[:8]}-${COUNT}"
    lock_behavior: unlockWhenFinished

    # ── Source material ────────────────────────────────────────────────────
    materials:
      git-repo:
        git: https://github.com/DileepJexpert/lightweight-ci-test-framework.git
        branch: main
        shallow_clone: true

    # ── Stages run sequentially; each must pass before the next starts ──────
    stages:

      # ── Stage 1: Build + Component Tests (no running service needed) ──────
      - build-and-component-tests:
          clean_workspace: true
          jobs:
            run-component-tests:
              elastic_profile_id: maven-java21-agent
              timeout: 10
              tasks:
                - exec:
                    command: mvn
                    arguments:
                      - clean
                      - test
                      - -Pcomponent
                      - -B          # batch mode — no ANSI colour in GoCD logs
              artifacts:
                - build:
                    source: target/surefire-reports/**
                    destination: test-reports/component

      # ── Stage 2: Build Docker image and push to ECR ──────────────────────
      - build-and-push-image:
          jobs:
            docker-build-push:
              elastic_profile_id: docker-agent
              timeout: 15
              tasks:
                - exec:
                    command: /bin/bash
                    arguments:
                      - -c
                      - |
                        IMAGE_TAG="${GO_PIPELINE_LABEL}"
                        ECR_URL="${ECR_REGISTRY}/loan-service"

                        aws ecr get-login-password --region ${AWS_REGION} \
                          | docker login --username AWS --password-stdin ${ECR_REGISTRY}

                        docker build -t ${ECR_URL}:${IMAGE_TAG} .
                        docker push ${ECR_URL}:${IMAGE_TAG}

                        # Save the tag for downstream stages
                        echo "${IMAGE_TAG}" > image-tag.txt
              artifacts:
                - build:
                    source: image-tag.txt
                    destination: image-info

      # ── Stage 3: Deploy to EKS and wait for pod to be Ready ──────────────
      - deploy-to-eks:
          approval:
            type: manual           # Remove this line for fully automated deploy
            roles:
              - deployers
          jobs:
            kubectl-deploy:
              elastic_profile_id: kubectl-agent
              timeout: 10
              tasks:
                - fetch:
                    pipeline: loan-service-delivery
                    stage: build-and-push-image
                    job: docker-build-push
                    source: image-info
                    destination: .
                - exec:
                    command: /bin/bash
                    arguments:
                      - -c
                      - |
                        IMAGE_TAG=$(cat image-info/image-tag.txt)
                        ECR_URL="${ECR_REGISTRY}/loan-service"

                        # Patch the deployment with the new image tag
                        kubectl set image deployment/loan-service \
                          loan-service=${ECR_URL}:${IMAGE_TAG} \
                          --namespace=${K8S_NAMESPACE}

                        # CRITICAL: block until pod is Running and Ready (1/1)
                        # GoCD will mark this stage FAILED if the pod never becomes healthy,
                        # which prevents Stage 4 (Karate) from running against a broken pod.
                        kubectl rollout status deployment/loan-service \
                          --namespace=${K8S_NAMESPACE} \
                          --timeout=300s

      # ── Stage 4: Karate Smoke Tests against live EKS service ─────────────
      - karate-smoke-tests:
          jobs:
            run-karate:
              elastic_profile_id: maven-java21-agent
              timeout: 15
              tasks:
                - exec:
                    command: mvn
                    arguments:
                      - verify
                      - -Psmoke
                      - -Dservice.base-url=${EKS_SERVICE_URL}
                      - -Dauth.token=${EKS_AUTH_TOKEN}
                      - -Dkarate.env=${KARATE_ENV}
                      - -B
              artifacts:
                - build:
                    source: target/karate-reports/**
                    destination: karate-reports
              tabs:
                Karate-Report: karate-reports/karate-summary.html
```

---

## GoCD Environment Variables to Configure

Set these in **GoCD Admin → Environments** or per-pipeline **Secure Variables**.

| Variable | Example Value | Where to set |
|---|---|---|
| `EKS_SERVICE_URL` | `https://loan-api.internal.company.com` | GoCD Environment |
| `EKS_AUTH_TOKEN` | `eyJhbGci...` | GoCD Secure Variable (encrypted) |
| `KARATE_ENV` | `qa` or `uat` or `production` | GoCD Environment |
| `ECR_REGISTRY` | `123456789.dkr.ecr.ap-south-1.amazonaws.com` | GoCD Environment |
| `AWS_REGION` | `ap-south-1` | GoCD Environment |
| `K8S_NAMESPACE` | `loan-service-qa` | GoCD Environment |

**How to set secure variables in GoCD:**
```
GoCD Admin → Pipelines → loan-service-delivery → Edit → Environment Variables
→ Add Variable → check "Secure" for auth tokens
```

---

## Running Against Multiple Environments

Create separate pipelines for QA, UAT, and Production using the same `.gocd.yaml` template
with different environment bindings.

```yaml
# qa-pipeline.gocd.yaml
pipelines:
  loan-service-qa:
    environment_variables:
      KARATE_ENV: qa
      EKS_SERVICE_URL: https://loan-api.qa.internal.company.com
      K8S_NAMESPACE: loan-service-qa
    # ... same stages as above

# uat-pipeline.gocd.yaml
pipelines:
  loan-service-uat:
    environment_variables:
      KARATE_ENV: uat
      EKS_SERVICE_URL: https://loan-api.uat.internal.company.com
      K8S_NAMESPACE: loan-service-uat
    # ... same stages as above
```

**Or use GoCD Environments feature** to bind different variable sets to the same pipeline template.

---

## Running Only Specific Tag Groups in Pipeline

You can pass Karate tag filters as a GoCD parameter:

```yaml
# In the Karate stage task arguments:
- mvn
- verify
- -Psmoke
- -Dservice.base-url=${EKS_SERVICE_URL}
- -Dkarate.options=--tags @smoke      # only @smoke tagged scenarios
```

**Useful tag combinations for different pipeline gates:**

| Pipeline Stage | Tag Filter | Purpose |
|---|---|---|
| Post-deploy quick check | `--tags @smoke` | All smoke tests (30 scenarios, ~6s) |
| Negative validation gate | `--tags @negative` | Only 4xx / failure scenarios |
| Performance gate | `--tags @performance` | Only SLA / responseTime assertions |
| Business regression | `--tags @business` | Full business journey tests |
| Callback contract check | `--tags @rest` | REST callback endpoint tests only |

---

## GoCD Pipeline Value Stream Map

GoCD's Value Stream Map shows the full path from commit to production.
Your pipeline contributes to it like this:

```
[Git commit] → [loan-service-delivery] → [loan-service-uat] → [loan-service-production]
                       │                          │                        │
               Stage 1: Tests           Stage 1: Tests            Stage 1: Tests
               Stage 2: Build           Stage 2: Deploy UAT       Stage 2: Deploy Prod
               Stage 3: Deploy QA       Stage 3: Karate UAT       Stage 3: Karate Prod
               Stage 4: Karate QA       (manual approval)         (manual approval)
```

---

## Karate HTML Report in GoCD

The pipeline config above adds a **tab** to the GoCD job view:

```yaml
tabs:
  Karate-Report: karate-reports/karate-summary.html
```

After the Karate stage runs, open the job in GoCD and click the **Karate-Report** tab.
The full HTML report with all 11 features, pass/fail per scenario, request/response bodies,
and response times is visible directly inside GoCD — no need to download the file.

---

## What Happens When Karate Fails in GoCD

```
Stage 4 (Karate) fails
        │
        ▼
GoCD pipeline turns RED ──────────────────────────────────────────────────┐
        │                                                                  │
        ▼                                                                  ▼
Downstream pipelines      GoCD notifies team                  Previous version
(UAT, Production) are     via email / Slack /                 stays live in EKS
BLOCKED automatically     webhook                             (rollout not promoted)
```

**GoCD notification config (in server config):**
```xml
<mailhost hostname="smtp.company.com" port="25" />
<security>
  <roles>
    <role name="deployers">
      <users><user>dileep</user></users>
    </role>
  </roles>
</security>
```

---

## GoCD Agent Requirements

Each stage uses a different agent profile. Make sure these are configured:

| Agent Profile | Needs | Used by |
|---|---|---|
| `maven-java21-agent` | Java 21, Maven 3.x | Stage 1 (component tests), Stage 4 (Karate) |
| `docker-agent` | Docker, AWS CLI | Stage 2 (image build/push) |
| `kubectl-agent` | kubectl, AWS CLI, kubeconfig | Stage 3 (EKS deploy + rollout) |

**Minimal agent setup for Stage 4 (Karate only):**
```bash
# On the GoCD agent machine / container
java -version    # must be 21+
mvn -version     # must be 3.6+
curl ${EKS_SERVICE_URL}/actuator/health  # agent must have network access to EKS
```

---

## Troubleshooting Common Pipeline Issues

| Symptom | Likely Cause | Fix |
|---|---|---|
| Stage 3 times out after 300s | Pod crashlooping or image pull error | Check `kubectl describe pod` and ECR permissions |
| Karate: `Invalid smoke test base URL` | `EKS_SERVICE_URL` not set or has placeholder | Verify GoCD environment variable is set for this pipeline |
| Karate: `Connection refused` | Pod is up but service/ingress not ready | Add a `curl --retry 5` health check after `kubectl rollout status` |
| Karate: `401 Unauthorized` | `EKS_AUTH_TOKEN` expired or wrong | Rotate token in GoCD secure variables |
| Karate: 29/30 pass, 1 fails | Async timing issue on slow EKS node | Increase `retry.count` in `karate-config.js` or `http.read-timeout-ms` |
| GoCD: pipeline not detected | YAML plugin not installed or path wrong | Check plugin is installed and file is `.gocd.yaml` extension |

---

## Quick Reference — Maven Commands by Stage

```bash
# Stage 1 — Component tests (no server, runs in build container)
mvn clean test -Pcomponent -B

# Stage 4 — Karate smoke tests (requires live EKS service)
mvn verify -Psmoke -B \
  -Dservice.base-url=https://loan-api.qa.internal.company.com \
  -Dauth.token=${EKS_AUTH_TOKEN} \
  -Dkarate.env=qa

# Run only @negative scenarios (fast validation gate)
mvn verify -Psmoke -B \
  -Dservice.base-url=${EKS_SERVICE_URL} \
  -Dkarate.options="--tags @negative"

# Run all tests (component + smoke) in one command
mvn verify -Pall -B \
  -Dservice.base-url=${EKS_SERVICE_URL} \
  -Dauth.token=${EKS_AUTH_TOKEN}
```
