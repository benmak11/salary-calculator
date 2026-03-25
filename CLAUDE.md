# Salary Calculator — Claude Notes

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
