# Salary Calculator — Claude Notes

## ADP-Parity Migration (2026-05-28)

Bringing the backend + incomatic iOS UI in line with ADP's public salary paycheck calculator
(`adp.com/.../salary-paycheck-calculator.aspx`). ADP exposes a four-tab input model
(Earnings / Federal Taxes / State or Territory Taxes / Benefits) plus a right-rail donut chart
and itemized summary. Full plan lives at `~/.claude/plans/eager-mapping-quiche.md`; this section
mirrors the phase table so we can track progress without that file.

### Scope decisions (locked-in)

- **Pay modes**: Salary + Hourly + Bonus (bonus stays a supplemental flat-22% earning line, already
  supported by the backend; iOS just needs to surface the input).
- **W-4 additions**: dependents amount, other income, itemized deductions, additional withholding,
  plus three exemption flags (federal / SS / Medicare). The "multiple jobs" toggle is captured in
  the UI but not yet actuated.
- **State coverage**: top-10 US states only — CA, NY, TX, FL, IL, PA, OH, GA, NC, MI. Existing
  CA + TX + MD stay; the other 7 get added. *(Subsequently expanded post-Phase-5 — see Phase 7.)*
- **Visualization**: replace the iOS 3-bar mini chart on the Insights tab with one donut chart
  (Take Home / Taxes / Benefits) + an ADP-style itemized right-rail summary keyed off a new
  `LineItem.category` field.

### Phased rollout

| Phase | Scope | Status |
|-------|-------|--------|
| **0** | Capture this section in `CLAUDE.md` | Done |
| **1** | Backend DTOs + enums: `Earnings`, `Salary`, `Hourly`, `W4`, `LineItemCategory`; extend `Pretax` (FSA), `Posttax` (Roth 401k), `LineItem` (category), `CountryOptions.US` (W4), `PayCadence` (+4 cadences). Engine still ignores the new fields. | Done |
| **2** | `CalculationOrchestrator` normalizes earnings → annual. `USCalculator` consumes W-4, FSA, Roth 401(k), exemption flags, hourly earnings; tags every emitted `LineItem` with a category. | Done |
| **3** | Rule pack: bump `US-2025.json` → `US-2025.11.0`, add MFJ federal brackets, add NY/FL/IL/PA/OH/GA/NC/MI state entries. | Done |
| **4** | iOS `CalculatorTab` 4-segment restructure (Earnings / Federal / State / Benefits); new request body shape; drives state list from `GET /v1/countries/US/states`; drops the HoH→SINGLE fallback. | Done |
| **5** | iOS `EarningsBreakdownView` donut + right-rail itemized summary; new `DonutChart` view; line-item parsing switches from name-pattern matching to `LineItem.category`. | Done |
| **6** | Unit tests refreshed for Phases 1–3. `CalculationInput.getRegularWagesAnnual()` got a back-compat fallback (`annualGross − supplementalAnnual`) so legacy tests that set `annualGross` directly without the salary/OT breakdown still work. 6 existing `USCalculatorTest` failures fixed (line-item renames: `bonus_withholding`→`supplemental_withholding`, `FICA (Social Security)`→`Social Security`, `Employee 401(k)`→`401(k)`). 43 new tests added across `DeductionCalculatorTest` (+8 — FSA, DCA, Roth helper), `CalculationInputTest` (+8 — earnings normalization, hourly, throws-when-empty, back-compat fallback), `USCalculatorTest` (+18 — earnings line items, FSA/DCA/HSA categories, Roth math on regular wages only, all 4 W-4 fields + exemption flags, state-code-prefixed line item name, every-item-has-a-category invariant), and `RulesRegistryTest` (+9 — version 11.0, 2025 std deductions, MFJ/HoH brackets, refreshed SS wage base, top-10 states present, no-SIT empty brackets, flat-rate single bracket, NY 9-bracket progressive, MD local rate). **203 tests pass, 0 failures.** | Done |
| **7** | Rule pack expanded beyond Phase 3's top-10 scope. User added ~28 additional state entries (AL, AK, AZ, AR, CO, CT, DE, HI, ID, IN, IA, KS, KY, LA, ME, MA, MN, MS, MO, MT, NE, NV, NH, NJ, NM, ND, OK + others) to `US-2025.json`. Also fixed a `"UpTo"` → `"upTo"` typo at line 54 (Alabama's first bracket) — Jackson is strict on field names so the typo had blocked all 14 US-loading tests from parsing the rule pack; the silent runtime bug would have made AL's 2% rate apply to all income instead of just the first $500. | Done |

### Out of scope (this migration)

Hourly tipped wages; multi-state withholding split; full 50-state coverage; YTD earnings carry-in
for SS-cap tracking; address geocoding; Pay Date as a SS-cap pivot (we accept the field but ignore
it); modern W-4 multiple-jobs adjustment table; county-tax beyond MD `local`; UK calculator changes;
Pub/Sub subscriber; `GET /v1/insights/{calculationId}`; PDF report endpoint; auth endpoints;
persistent calculation history.

### How we'll track progress

Each phase lands as its own commit / review pass. After completing a phase, flip its Status column
above to `Done` so this file stays the source of truth for what's shipped vs pending.

---

## Spring Boot → Javalin Migration (2026-05-25)

The entire backend was migrated off Spring Boot onto **Javalin 6.x** to slim the runtime
footprint and remove Spring Cloud GCP from the rule-pack-service. There is no Spring on
the classpath in any module any longer.

### What changed

| Concern                  | Before                                          | After                                                                |
| ------------------------ | ----------------------------------------------- | -------------------------------------------------------------------- |
| HTTP framework           | Spring Boot Web (Tomcat)                        | Javalin 6.3 on Jetty, virtual threads enabled                        |
| Dependency injection     | `@Component` / `@Autowired` / Spring context    | Plain constructor injection wired in `Main.java`                     |
| JSON                     | Spring Jackson auto-config                      | Jackson + `JavalinJackson` mapper                                    |
| Validation               | Spring `@Valid` controller param                | Jakarta Validation runtime (Hibernate Validator) via `RequestValidator` |
| Exception handling       | `@RestControllerAdvice` / `@ExceptionHandler`    | `app.exception(Class, handler)` in `Main`                            |
| Caching (api/rules-registry) | Spring Cache + Redis backend                | Caffeine in-process (no Redis dep)                                   |
| Caching (rule-pack-service)  | Spring `@Cacheable("rulepack")` + Memorystore | `RulePackCache` (Caffeine, 10-min TTL); `invalidateAll()` on publish/deprecate |
| Firestore client         | `spring-cloud-gcp-starter-data-firestore` reactive | `google-cloud-firestore` blocking client + hand-rolled `RulePackRepository` |
| Pub/Sub                  | `PubSubTemplate` (spring-cloud-gcp)             | `google-cloud-pubsub` `Publisher` via `RulePackLifecyclePublisher`   |
| GCS                      | `spring-cloud-gcp-starter-storage`              | `google-cloud-storage` directly                                      |
| GraphQL                  | `spring-boot-starter-graphql` (annotation-driven) | `graphql-java` directly; SDL loaded from `resources/graphql/schema.graphqls` |
| HTTP client (api → rule-pack-service) | `RestTemplate`                     | `java.net.http.HttpClient`                                           |
| OpenAPI                  | `springdoc-openapi-starter-webmvc-ui`           | Removed; DTOs keep `swagger-annotations` for future re-introduction  |
| Actuator                 | `spring-boot-starter-actuator` + Prometheus     | Hand-rolled `/actuator/health` + `/actuator/prometheus` (Micrometer) |
| Packaging                | `bootJar`                                       | `shadowJar` via `com.gradleup.shadow`                                |

### Key files

- `modules/api/src/main/java/app/salary/api/Main.java` — wires everything for the customer-facing API on `:8080`.
- `modules/rule-pack-service/src/main/java/app/salary/rulepack/Main.java` — boots the rule-pack service on `:8081`. GCP clients are best-effort: when `ENABLE_GCP=false` (the docker-compose default) the persistence routes return 503 but the service still boots cleanly.
- `Dockerfile` + `Dockerfile.rule-pack-service` — multi-stage builds that publish `shadowJar` artifacts.
- `docker-compose.yml` — brings up both services on the same network; no Redis sidecar needed.

### Known follow-up items

- **Tests were dropped, not ported.** All Spring `MockMvc` controller tests in `modules/api`, all integration tests in `modules/integration-tests`, plus three rule-pack-service tests (`RulePackControllerTest`, `RulePackGraphQLControllerTest`, `RulePackServiceTest`) were removed. They need to be rewritten using `io.javalin:javalin-testtools` (`JavalinTest.test(...)`). See `modules/integration-tests/TODO_REWRITE.md`.
- **`HttpRulePackClientTest`** was deleted (mocked `RestTemplate`). Replace with a mock `HttpClient` or an in-process Javalin server returning canned rule-pack JSON.
- **`RULE_PACK_SERVICE_URL` is wired by default in docker-compose** to `http://rule-pack-service:8081`. Because `ENABLE_GCP=false`, the rule-pack-service returns 503 for `/v1/rule-packs/*`, and the api transparently falls back to the embedded classpath rule pack. To exercise the real Firestore path locally, set `ENABLE_GCP=true` and mount Google Application Default Credentials.
- **CORS** is currently open-to-all on the rule-pack-service (`anyHost()`) — tighten before any production deploy.
- **Pub/Sub subscriber** in the api module (called out in the earlier follow-ups list below) is still not implemented — events publish from the rule-pack-service but no consumer evicts the api's `RulesRegistry` Caffeine cache.

### How to run locally

```bash
./gradlew :modules:api:shadowJar :modules:rule-pack-service:shadowJar
docker compose up --build
# api          → http://localhost:8080  (try GET /v1/health, POST /v1/calculate)
# rule-pack    → http://localhost:8081  (try GET /actuator/health)
```

---

## GCP Datastore & API Improvements

These improvements were identified on 2026-03-24 and **fully implemented on 2026-03-24**.

---

## Implemented Changes

### 1. Connect `CalculationOrchestrator` to the Rule Pack Service ✅

**Implemented**: 2026-03-24

**New files**:
- `modules/calculator/src/main/java/app/salary/calculator/client/RulePackClient.java` — interface
- `modules/api/src/main/java/app/salary/api/client/HttpRulePackClient.java` — HTTP implementation

**Modified files**:
- `modules/calculator/src/main/java/app/salary/calculator/engine/CalculationOrchestrator.java` — accepts optional `RulePackClient`, tries remote first, falls back to classpath `RulesRegistry`
- `modules/api/src/main/java/app/salary/api/config/CalculatorConfig.java` — wires up `RestTemplate`, `HttpRulePackClient`; added `@EnableCaching`
- `modules/api/src/main/resources/application.yml` — added `rulepack.service.base-url: ${RULE_PACK_SERVICE_URL:}`

**How it works**:
- `HttpRulePackClient` calls `GET /v1/rule-packs/latest?country=&taxYear=` then `GET /v1/rule-packs/{id}/download`
- Any HTTP error or empty response silently falls back to the embedded classpath JSON (no service disruption)
- Set env var `RULE_PACK_SERVICE_URL=http://rule-pack-service:8081` to enable; leave blank to use classpath only

**Known follow-up**: The calculator/api module has no local cache for rule packs fetched via HTTP. Each
calculation that hits the HTTP path makes two requests to rule-pack-service. The service's Redis cache
absorbs most of the cost, but adding a short-lived local cache (`@Cacheable`) to `HttpRulePackClient`
would further reduce latency for hot paths.

---

### 2. Replace PostgreSQL with Firestore for Rule Pack Metadata ✅

**Implemented**: 2026-03-24

**Modified files**:
- `modules/rule-pack-service/src/main/java/app/salary/rulepack/entity/RulePackEntity.java` — replaced all JPA annotations (`@Entity`, `@Table`, `@Column`, `@PrePersist`, `@PreUpdate`) with `@Document(collectionGroup = "rule-packs")` and `@DocumentId`; date fields changed from `LocalDate`/`LocalDateTime` to `java.util.Date` (maps to Firestore Timestamp natively)
- `modules/rule-pack-service/src/main/java/app/salary/rulepack/repository/RulePackRepository.java` — now extends `FirestoreReactiveRepository<RulePackEntity, String>`; all custom JPA query methods removed
- `modules/rule-pack-service/src/main/java/app/salary/rulepack/service/RulePackService.java` — removed `@Transactional`; all repository calls use `.block()` / `.blockOptional()` on reactive Flux/Mono; all filtering is done in-memory after `findAll()` to avoid composite Firestore index requirements; `toDto()` converts `Date` → `LocalDate`/`LocalDateTime` via `ZoneId.systemDefault()`
- `modules/rule-pack-service/build.gradle` — removed `spring-boot-starter-data-jpa`, `postgresql`; added `spring-cloud-gcp-starter-data-firestore`
- `modules/rule-pack-service/src/main/resources/application.properties` — removed all `spring.datasource.*` and `spring.jpa.*` config

**Firestore collection**: `rule-packs/{id}`

**Known follow-up**: The current implementation uses `findAll()` with in-memory filtering for every
query. This is acceptable for the expected low cardinality of rule packs (<1000 documents), but if the
collection grows significantly, targeted Firestore queries with composite indexes should replace the
in-memory approach. Composite indexes would be required for multi-field equality + ordering queries
(e.g., `countryCode == X AND taxYear == Y AND status == PUBLISHED ORDER BY createdAt DESC`).

---

### 3. Add GraphQL for Mobile ✅

**Implemented**: 2026-03-24

**New files**:
- `modules/rule-pack-service/src/main/resources/graphql/schema.graphqls` — full schema
- `modules/rule-pack-service/src/main/java/app/salary/rulepack/graphql/RulePackGraphQLController.java` — `@QueryMapping` and `@MutationMapping` handlers
- `modules/rule-pack-service/src/main/java/app/salary/rulepack/graphql/RulePackGraphQL.java` — GraphQL response record (all dates as ISO strings)

**Modified files**:
- `modules/rule-pack-service/build.gradle` — added `spring-boot-starter-graphql`

**Endpoint**: `POST /graphql` (alongside all existing REST endpoints — none were removed)

**Schema** (`schema.graphqls`):
```graphql
type RulePack {
  id: ID!
  country: String!
  taxYear: Int!
  version: String!
  status: RulePackStatus!
  effectiveDate: String
  storagePath: String
  checksum: String
  createdAt: String
}

enum RulePackStatus { DRAFT  PUBLISHED  DEPRECATED }

type Query {
  latestRulePack(country: String!, taxYear: Int!): RulePack
  rulePacks(country: String, taxYear: Int, status: RulePackStatus, page: Int, size: Int): [RulePack!]!
  rulePack(id: ID!): RulePack
}

type Mutation {
  createRulePack(country: String!, taxYear: Int!, version: String!, effectiveDate: String!): RulePack!
  publishRulePack(id: ID!): RulePack!
  deprecateRulePack(id: ID!): RulePack!
}
```

**Known follow-up**: The `createRulePack` GraphQL mutation creates a metadata entry with an empty
rule pack body stored in GCS. Rule pack content upload must still be done via the REST endpoint
(`POST /v1/rule-packs`). If content upload via GraphQL is needed, a `content: String` argument (JSON
string) should be added to the mutation and schema.

---

### 4. Replace Caffeine with Cloud Memorystore (Redis) for Caching ✅

**Implemented**: 2026-03-24

**Modified files**:
- `modules/rule-pack-service/build.gradle` — removed `caffeine`; added `spring-boot-starter-data-redis`
- `modules/rule-pack-service/src/main/resources/application.properties` — `spring.cache.type=redis`, `spring.data.redis.host=${REDIS_HOST:localhost}`
- `modules/api/build.gradle` — added `spring-boot-starter-cache`, `spring-boot-starter-data-redis`
- `modules/api/src/main/resources/application.yml` — `spring.cache.type=redis`, `spring.data.redis.host=${REDIS_HOST:localhost}`
- `modules/api/src/main/java/app/salary/api/config/CalculatorConfig.java` — added `@EnableCaching`
- `modules/rules-registry/build.gradle` — replaced `caffeine` with `spring-context` (for `@Cacheable`)
- `modules/rules-registry/src/main/java/app/salary/rules/RulesRegistry.java` — removed embedded Caffeine cache; `getRulePack()` annotated with `@Cacheable("rulePacks")`; `clearCache()` annotated with `@CacheEvict(value = "rulePacks", allEntries = true)`

**Cache names**:
- `rulepack` — used by `RulePackService.findLatest()` in the rule-pack-service; evicted by `@CacheEvict` on `publishRulePack()` and `deprecateRulePack()`
- `rulePacks` — used by `RulesRegistry.getRulePack()` in the api/calculator module (fallback classpath cache)

**Required env var**: `REDIS_HOST` — points to Cloud Memorystore instance. Defaults to `localhost` for local dev.

**Known follow-up**: `RulesRegistry` caching via `@Cacheable` only activates when `RulesRegistry` is
accessed through its Spring proxy (i.e., as a `@Bean` in a Spring context). In a plain unit test that
instantiates `new RulesRegistry()` directly, no caching applies and each call reloads from classpath.
This is by design and reflected in the updated unit tests.

---

### 5. Emit Pub/Sub Events on Rule Pack Status Changes ✅

**Implemented**: 2026-03-24

**New files**:
- `modules/rule-pack-service/src/main/java/app/salary/rulepack/event/RulePackLifecycleEvent.java` — event payload POJO

**Modified files**:
- `modules/rule-pack-service/build.gradle` — added `spring-cloud-gcp-starter-pubsub`
- `modules/rule-pack-service/src/main/java/app/salary/rulepack/service/RulePackService.java` — injects `PubSubTemplate`; calls `publishLifecycleEvent()` after `publishRulePack()` and `deprecateRulePack()`
- `modules/rule-pack-service/src/main/resources/application.properties` — added `gcp.pubsub.topic.rulepack-lifecycle=${PUBSUB_TOPIC:rule-pack-lifecycle}`

**Event payload**:
```json
{
  "event": "RULE_PACK_PUBLISHED",
  "country": "US",
  "taxYear": 2025,
  "version": "US-2025.10.0",
  "storagePath": "rulepack/US/2025/..."
}
```

**Event types**: `RULE_PACK_PUBLISHED`, `RULE_PACK_DEPRECATED`

**Known follow-up**: The **subscriber side is not yet implemented**. The calculator/api service needs
a Pub/Sub subscription listener that receives these events and calls `rulesRegistry.clearCache()` to
evict stale classpath rule pack entries. Without the subscriber, the events are published to GCP
Pub/Sub but nothing consumes them. To implement:
1. Add a `@PubSubSubscriber` or `PubSubTemplate.subscribe()` call in the api module
2. On `RULE_PACK_PUBLISHED`, call `rulesRegistry.clearCache()` so the next request re-fetches from rule-pack-service via HTTP
3. Required env var: a subscription name pointing to the `rule-pack-lifecycle` topic

---

## Current Architecture Summary

| Concern | Before | After |
|---|---|---|
| Rule pack source (calculator) | Classpath JSON only | HTTP → rule-pack-service, fallback to classpath |
| Rule pack metadata store | PostgreSQL (Cloud SQL) | Firestore (`rule-packs` collection) |
| Rule pack API | REST only | REST + GraphQL (`/graphql`) |
| Caching backend | Caffeine (in-process) | Redis (Cloud Memorystore, shared) |
| Cache invalidation | TTL-based only | TTL + `@CacheEvict` on publish/deprecate + Pub/Sub events (subscriber pending) |
| Status change notifications | None | Pub/Sub topic `rule-pack-lifecycle` |

## Environment Variables

| Variable | Service | Purpose | Default |
|---|---|---|---|
| `RULE_PACK_SERVICE_URL` | api | Base URL for rule-pack-service HTTP calls | `""` (classpath fallback) |
| `REDIS_HOST` | api, rule-pack-service | Cloud Memorystore Redis host | `localhost` |
| `GCP_PROJECT_ID` | rule-pack-service | GCP project for Firestore + Pub/Sub + GCS | `salary-calculator-dev` |
| `GCP_BUCKET_RULEPACKS` | rule-pack-service | GCS bucket for rule pack content | `salary-calculator-rulepacks` |
| `PUBSUB_TOPIC` | rule-pack-service | Pub/Sub topic for lifecycle events | `rule-pack-lifecycle` |

## Remaining Follow-Up Items

| Priority | Item | File(s) | Notes |
|---|---|---|---|
| High | Implement Pub/Sub subscriber in api module | `modules/api/` | Evicts `rulePacks` cache on `RULE_PACK_PUBLISHED`; requires subscription name env var |
| Medium | Add local cache to `HttpRulePackClient` | `modules/api/src/main/java/app/salary/api/client/HttpRulePackClient.java` | Reduces round-trips to rule-pack-service for hot calculation paths |
| Medium | Replace in-memory Firestore filtering with targeted queries + indexes | `modules/rule-pack-service/src/main/java/app/salary/rulepack/service/RulePackService.java` | Only needed if `rule-packs` collection grows beyond ~1000 documents |
| Low | Add `content: String` argument to `createRulePack` GraphQL mutation | `modules/rule-pack-service/src/main/resources/graphql/schema.graphqls` + `RulePackGraphQLController.java` | Enables full rule pack creation via GraphQL without needing REST |

---

## Incomatic iOS — Required API Changes

**Identified**: 2026-03-30
**Trigger**: Calculator tab redesign ("Define your Financial Horizon") and Insights tab redesign ("Earnings Breakdown").
**Status**: All items below are **not yet implemented**. iOS uses workarounds noted for each.

---

### 1. Add `HEAD_OF_HOUSEHOLD` to `FilingStatus` enum — High

**Identified**: 2026-03-30

The redesigned Calculator tab exposes a third filing status radio button: **Head of Household**. The backend `FilingStatus` enum only contains `SINGLE` and `MARRIED`.

**Files to change**:
- `modules/common/src/main/java/app/salary/common/constants/FilingStatus.java` — add `HEAD_OF_HOUSEHOLD`
- `modules/calculator/src/main/java/app/salary/calculator/us/` — add HoH tax bracket logic (deduction is $21,900 for 2025, distinct bracket schedule from SINGLE)

**iOS workaround**: `HEAD_OF_HOUSEHOLD` selection falls back to `SINGLE` in the API call until this is implemented. See `ContentView.swift` `triggerCalculation()`.

---

### 2. Add individual benefit fields to `Pretax` DTO — High

**Identified**: 2026-03-30

The redesigned Calculator tab has **Medical**, **Dental**, and **Vision** as individual on/off toggles. The backend `Pretax` object has a single `fixed: Double` field that cannot distinguish between benefit types. This prevents the backend from:
- Applying correct tax treatment per benefit (e.g., vision premiums may be post-tax depending on plan)
- Returning individual benefit line items in the response

**Files to change**:
- `modules/common/src/main/java/app/salary/common/dto/Pretax.java` — add `medical`, `dental`, `vision` fields
- Calculator engine — consume the new fields and emit named line items (`"Medical Premium"`, `"Dental Premium"`, `"Vision Premium"`) instead of a combined `"Pre-tax Deductions"` line item

**iOS workaround**: medical + dental + vision annual amounts are hardcoded ($3,000 / $600 / $180) and summed into `pretax.fixed`. The Insights screen displays them from local state, not from API line items.

---

### 3. Add `bonus` / supplemental wages field to `CalculateRequest` — High

**Identified**: 2026-03-30 (carried from 2026-03-29 Insights redesign)

Bonus and overtime income is subject to a flat **22% federal supplemental withholding rate**, not the marginal bracket rate applied to regular wages. The iOS currently combines bonus into `annualSalary`, so it is taxed incorrectly.

**Files to change**:
- `modules/common/src/main/java/app/salary/common/dto/CalculateRequest.java` — add `bonus: Double`
- `modules/common/src/main/java/app/salary/common/dto/CalculateResponse.java` — add `baseSalaryPerCadence` and `bonusPerCadence` to the response so iOS can display them separately in the Gross Pay card
- Calculator engine — apply flat 22% withholding on bonus amount before adding to regular tax calculation

**iOS workaround**: bonus is summed into `annualSalary`; `bonusAnnual: 0` passed to the service in the current Calculator tab design.

---

### 4. Add named/itemized custom deductions to `Pretax` / `Posttax` — Medium

**Identified**: 2026-03-30

The "Other Deductions" card allows users to add **N custom deduction line items**. The backend `Pretax.fixed` and `Posttax.fixed` are single scalar values and cannot represent a list of named deductions. This means:
- The backend cannot label them individually in response `lineItems`
- The iOS has no way to distinguish which portion of `fixed` came from which custom entry

**Files to change**:
- `modules/common/src/main/java/app/salary/common/dto/Pretax.java` — replace `fixed: Double` with `customDeductions: List<NamedDeduction>`
- New DTO: `NamedDeduction { name: String, amount: Double }`
- Calculator engine — emit one `LineItem` per `NamedDeduction` entry

**iOS workaround**: All custom deduction amounts are summed into `pretax.fixed` as a single value.

---

### 5. Add `GET /v1/insights/{calculationId}` — Personalised Smart Insight — High

**Identified**: 2026-03-30

The Insights tab "Smart Saving Insight" card displays specific, calculation-driven numbers:
- *"reduce your taxable income by $78.50 per month"* — 1% of monthly gross
- *"securing an additional $1,884 for your retirement annually including employer match"* — (1% employee + 1% employer match) × annual salary

Currently these are approximated **client-side** using:
- `annualTotal * 0.01 / 12` for monthly contribution
- `annualTotal * 0.01 * 2` for annual retirement (assumes 100% employer match — this will be wrong for most employers)
- Effective tax rate as a proxy for marginal rate — systematically understates the savings for higher earners

A backend endpoint would provide employer-specific match rates, accurate marginal rates, and state-specific retirement tax incentives.

**New endpoint**:
```
GET /v1/insights/{calculationId}
Response:
{
  "type": "RETIREMENT_OPTIMIZATION",
  "headline": "Smart Saving Insight",
  "monthlyTaxSavings": 78.50,
  "annualRetirementBenefit": 1884.00,
  "recommendedContributionPercent": 7,
  "body": "By increasing your 401k contribution by just 1%...",
  "ctaLabel": "Optimize 401k"
}
```

**iOS workaround**: Numbers computed locally in `EarningsBreakdownView` (see `insight401kMonthlyContribution`, `insight401kAnnualRetirement` computed vars). Employer match is hardcoded as 100% (1:1). See `ContentView.swift`.

---

### 6. Add `POST /v1/reports/pdf` — PDF Report Download — Medium

**Identified**: 2026-03-30 (carried from 2026-03-29 Insights redesign)

The "Download PDF Report" button on the Insights tab requires a server-side PDF generation endpoint that accepts a `calculationId` and returns a downloadable PDF payslip.

**New endpoint**:
```
POST /v1/reports/pdf
Request:  { "calculationId": "c_abc123" }
Response: application/pdf  (or { "downloadUrl": "<signed GCS URL>" })
```

**iOS workaround**: Button shows an alert explaining the feature is coming soon.

---

### 7. Add `GET /v1/countries/US/states` — State List Endpoint — Low

**Identified**: 2026-03-30 (carried from 2026-03-29 analysis)

The "Tax Location" dropdown hardcodes all 50 US state names and their 2-letter codes in `ContentView.swift`. A backend endpoint would keep this list authoritative and allow adding territories (DC, PR, etc.) without an app update.

**New endpoint**:
```
GET /v1/countries/US/states
Response: [{ "code": "CA", "name": "California" }, ...]
```

**iOS workaround**: `usStates` array and `stateCodeMap` dictionary hardcoded in `CalculatorTab`.

---

### 8. Support dynamic tax years — Low

**Identified**: 2026-03-30 (carried from 2026-03-29 analysis)

`taxYear: 2025` is hardcoded in `CalculatorTab.triggerCalculation()`. When 2026 rule packs are published, the app cannot discover them without an update.

**Enhancement**: Augment `GET /v1/countries` response to include `supportedTaxYears: [Int]` per country, or add `GET /v1/tax-years?country=US`.

---

