# Testing Documentation

## Overview
Unit tests per module (JUnit 5 + Mockito), plus Javalin-level HTTP tests via
`JavalinTest` in `modules/api`. No separate integration-test module — API
route tests spin up a real (in-memory-backed) Javalin app per test class.

## Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Tests for a Specific Module
```bash
./gradlew :modules:api:test
./gradlew :modules:calculator:test
./gradlew :modules:common:test
./gradlew :modules:rule-pack-service:test
./gradlew :modules:rules-registry:test
```

### Full Quality Gate (tests + coverage + static analysis)
```bash
./gradlew build
```
Runs tests, Checkstyle, SpotBugs, and the JaCoCo ≥80% coverage verification
gate together — this is what CI enforces as blocking.

## Test Coverage by Module

- **`modules/calculator`** — `USCalculator`, `UKCalculator`, `TaxBracketCalculator`,
  `DeductionCalculator`. Federal/state/FICA math, both W-4 paths (modern +
  legacy pre-2020 allowances), Roth vs. Traditional 401(k) tax treatment,
  bonus/commission/RSU supplemental income and its inclusion rules, pay
  cadence conversions.
  **Location**: `modules/calculator/src/test/java/app/salary/calculator/`
- **`modules/api`** — Route-level HTTP tests via `JavalinTest.test(app, (server, client) -> {...})`
  for every controller: `CalculateController`, `AuthController`,
  `CalculationHistoryController`, `GrantsController`, `BudgetController`,
  `BudgetPlanController`, `StocksController`, `AccountController`. Covers
  request validation, auth (401 on missing/invalid session token), and
  provider-down fallbacks (Vertex AI / Finnhub unavailable → 503).
  **Location**: `modules/api/src/test/java/app/salary/api/`
- **`modules/common`** — DTO validation constraints (Jakarta Validation).
  **Location**: `modules/common/src/test/java/app/salary/common/`
- **`modules/rule-pack-service`** — Rule-pack CRUD, Firestore/GCS/Pub-Sub
  wiring via mocks, publisher/user-directory logic.
  **Location**: `modules/rule-pack-service/src/test/java/app/salary/rulepack/`
- **`modules/rules-registry`** — Embedded rule-pack JSON loading/parsing.

## Code Coverage

Enforced via JaCoCo (`./gradlew jacocoTestCoverageVerification`, folded into
`./gradlew build`), **minimum 80% per module**.

### What's excluded from the gate (`jacocoExcludes` in root `build.gradle`)
- Simple DTOs annotated `@ExcludeFromCodeCoverage` — data containers with no
  business logic (`CalculateRequest`, `CalculateResponse`, `Pretax`,
  `Posttax`, `Budget`, `RsuGrant`, `SupplementalBreakdown`, and similar; see
  the annotation usages for the full current list)
- `**/repository/**`, `Main.class`, `**/*Application.class`
- **Firestore-backed stores** (`**/store/Firestore*.class`) and GCP client
  wiring (`GoogleIdTokenSupplier`, `VertexGenerativeAiClient`) — these need
  live GCP credentials and are deliberately untested locally; the
  in-memory-store counterparts (`InMemoryCalculationStore`,
  `InMemoryUserDirectory`, `InMemoryGrantStore`, `InMemoryBudgetStore`, etc.)
  carry the coverage instead. This mirrors `sonar.coverage.exclusions` in the
  same file — keep both lists in sync when adding a new Firestore-backed
  class or GCP client.

## CI/CD Integration

`.github/workflows/ci.yml` runs on push to `main` and on PRs targeting `main`:

**1. `test`** — `./gradlew build -x test` then `./gradlew test`; publishes a
JUnit test report.

**2. `lint`** — `./gradlew checkstyleMain checkstyleTest spotbugsMain`,
blocking. Uploads Checkstyle/SpotBugs HTML reports as artifacts.

**3. `sonar`** — `./gradlew test jacocoTestReport sonar` against SonarQube
Cloud. Guarded on `SONAR_TOKEN` being set as a repo secret — skips (green, not
red) when it isn't, so CI stays green while SonarCloud project setup is
pending.

**4. `build-docker`** (only on push to `main`, after `test` + `lint` pass) —
builds the `api` shadowJar and a Docker image.

Deploys are a separate workflow (`.github/workflows/deploy.yml`), triggered
independently on push to `main` via GitHub Actions + Workload Identity
Federation — see the salary-calculator `CLAUDE.md` for deploy specifics
(env-var replacement gotcha, secrets wiring order, Cloud Run-to-Cloud Run
ingress).

## Best Practices

1. **Run `./gradlew build` before pushing** — it's the same gate CI enforces (tests + Checkstyle + SpotBugs + JaCoCo ≥80%).
2. **Write tests for new features**, including the 401/503/validation-error paths, not just the happy path.
3. **New Firestore-backed classes need the exclusion added to both `jacocoExcludes` and `sonar.coverage.exclusions`** in the root `build.gradle` — a class that needs live GCP will otherwise fail the local coverage gate the first time it's added (this has happened before; see the salary-calculator `CLAUDE.md` for the incident).
4. **Never require a Firestore emulator** — `ENABLE_GCP=false` (the CI/test default) makes all GCP-backed stores fall back to in-memory implementations by design.
5. **Mock external dependencies** — `GenerativeAiClient` and `StockClient` are interfaces specifically so `BudgetPlanController`/`StocksController` tests can substitute a working/failing lambda without touching Vertex AI or Finnhub.

## Troubleshooting

### Tests failing after adding a new module or class
1. Check that any new `@ExcludeFromCodeCoverage` DTO or Firestore-backed class
   is reflected in `jacocoExcludes` if it should be excluded from the 80% gate.
2. Verify rule pack JSON files are present under `modules/rules-registry/src/main/resources/rulepacks/`.
3. For `modules/api` route tests, confirm the test wires the controller with
   in-memory stores/mocked clients — never live GCP or Vertex AI.

### JaCoCo coverage gate failing locally but not before
A newly added, genuinely untested class (often a new Firestore-backed store or
GCP client) is being counted against the 80% minimum. Either add real tests,
or — only if it truly needs live GCP/Vertex AI credentials to test —add it to
`jacocoExcludes` (and mirror the exclusion in `sonar.coverage.exclusions`).

## Future Improvements

- [ ] Re-introduce an OpenAPI/Swagger UI spec (DTOs already carry `swagger-annotations` from the pre-Javalin-migration setup)
- [ ] Mutation testing with PIT
- [ ] Contract testing with Pact
- [ ] Performance/load testing
