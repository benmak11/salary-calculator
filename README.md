# Salary Calculator Microservice

A production Javalin microservice that computes take-home pay for the US and
UK, with detailed tax breakdowns, and backs the `incomatic` iOS app. Deployed
to Cloud Run as two services (`api` + `rule-pack-service`).

## 🌟 Features

- 🌍 **Multi-country support** (US — 40+ states, UK — easily extensible)
- 💰 **Multiple pay cadences** (annual, semiannual, quarterly, monthly, semimonthly, biweekly, weekly, daily)
- 📊 **Detailed tax breakdown** by category (earnings / federal / FICA / state / pre-tax benefit / retirement / post-tax / net)
- 🇺🇸 **Modern + legacy US W-4 support** — post-2020 (dependents credit, other income, itemized deductions, additional withholding) and pre-2020 allowances-based withholding, gated per-request
- 🩺 **FSA / HSA / Traditional + Roth 401(k) / per-benefit premiums** (medical, dental, vision) modeled as discrete line items
- 💵 **Bonus, commission, and RSU vesting** as structured, dated supplemental income — taxed at the flat IRS supplemental rate, broken out separately in the response
- 🔐 **Sign in with Apple** — mints a 30-day session JWT; calculation history, RSU grants, and household budget are all saved per-user in Firestore
- 📈 **Stock quote proxy** (Finnhub) for RSU grant pricing, auth-gated to protect the API key
- 🤖 **AI-generated budget plans** — Gemini (Vertex AI) suggests per-goal savings contributions, verified against a deterministic on-device engine on the client
- 📝 **Human-readable explanations** for each calculation
- 🔄 **Pluggable** country calculators (registered explicitly in `Main.java`, no reflection/DI framework)
- 🐳 **Docker ready**, 📈 **Production monitoring** (Prometheus, health checks)

## 🏗️ Architecture

Hand-wired constructor injection throughout — **no Spring, no DI framework**.
Everything is composed in `Main.java`.

| Module | Responsibility |
| --- | --- |
| `modules/common` | Shared DTOs + Jakarta Validation + Swagger annotations |
| `modules/calculator` | Country calculators (`USCalculator`, `UKCalculator`) + shared tax/deduction helpers. Pure logic, no I/O |
| `modules/rules-registry` | Embedded classpath rule packs (`US-2025.json`, `UK-2025.json`) — the fallback used when `rule-pack-service` is unreachable |
| `modules/api` | Public REST surface: calculate, auth, history, grants, budget, stocks. Wires everything in `Main.java` |
| `modules/rule-pack-service` | Locked-down rule-pack CRUD (Firestore + GCS + Pub/Sub), reached via internal Google ID token auth |

**GCP is optional at runtime.** With `ENABLE_GCP=false` (or no credentials
present), every GCP-backed store falls back to an in-memory implementation —
no Firestore emulator required for local dev or tests.

### Supported Countries
- 🇺🇸 **United States** (Federal + 40+ State taxes incl. CA/NY/TX/FL/IL/PA/OH/GA/NC/MI, FICA, Medicare, additional-Medicare surtax)
- 🇬🇧 **United Kingdom** (Income Tax, National Insurance, Student Loans, Pension)

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Docker (optional)

### Option 1: Using Setup Script
```bash
chmod +x setup.sh
./setup.sh
```

### Option 2: Manual Setup
```bash
# Build everything (compiles all modules, runs unit tests)
./gradlew clean build

# Run the API on :8080 — via the Gradle `application` plugin's `run` task
./gradlew :modules:api:run

# Optionally run the rule-pack-service on :8081 in another terminal.
# ENABLE_GCP=false boots it without Firestore/GCS/Pub-Sub, so the API
# transparently falls back to its embedded classpath rule pack.
ENABLE_GCP=false ./gradlew :modules:rule-pack-service:run
```

Or run the packaged fat JARs (built with `shadowJar`):
```bash
./gradlew :modules:api:shadowJar :modules:rule-pack-service:shadowJar
java -jar modules/api/build/libs/salary-calculator-api-1.0.0-all.jar
```

### Option 3: Docker
```bash
# Multi-stage builds compile the shadowJars inside the images, then start the
# API (:8080) and rule-pack-service (:8081) on a shared network.
docker compose up --build

# Optional Prometheus + Grafana monitoring stack:
docker compose --profile monitoring up --build
```

## 🔑 Environment Variables

The API reads configuration from environment variables (see `Env` in `Main.java`):

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP listen port |
| `RULE_PACK_SERVICE_URL` | empty | Rule-pack-service base URL. Empty = use embedded classpath rule pack |
| `RULE_PACK_AUDIENCE` | empty | OIDC audience for service-to-service tokens (Cloud Run internal calls) |
| `GCP_PROJECT_ID` | `salary-calculator-dev` | GCP project for Firestore (user directory, calculation history, grants, budget) |
| `ENABLE_GCP` | auto | When `true`, connects to Firestore. Auto-detected from `GOOGLE_APPLICATION_CREDENTIALS` / `GOOGLE_CLOUD_PROJECT`. Set `false` to force in-memory stores |
| `APPLE_AUDIENCE` | empty | iOS bundle ID used as the `aud` claim when verifying Apple identity tokens. Leaving this empty disables Sign in with Apple |
| `SESSION_JWT_SECRET` | (generated) | Base64-encoded (or 32+ raw UTF-8 bytes) HS256 secret for signing our own session JWTs. If unset, a random 32-byte secret is generated at boot — sessions don't survive restart |
| `FINNHUB_API_KEY` | empty | Finnhub API key for the stock search/quote proxy. Empty = `/v1/stocks/*` returns 503 |
| `FINNHUB_BASE_URL` | `https://finnhub.io/api/v1` | Finnhub base URL override |
| `VERTEX_AI_LOCATION` | `global` | Vertex AI location for Gemini calls. Must be `global` for globally-routed models like `gemini-3.1-flash-lite` — a regional value 404s for global-only models |
| `VERTEX_AI_MODEL` | `gemini-3.1-flash-lite` | Gemini model used for AI budget-plan generation |

## 📡 API Endpoints

### Calculate Salary
```bash
POST /v1/calculate
```
When called with a valid `Authorization: Bearer <sessionToken>` (see below), the
calculation is auto-saved to the caller's history and the response's
`calculationId` is the Firestore document id. Anonymous calls work identically
— no persistence, no auth required.

### Sign in with Apple
```bash
POST /v1/auth/apple
```
Body: `{ "identityToken": "<JWT from ASAuthorizationAppleIDProvider>", "nonce": "<raw nonce>", "displayName": "<optional>" }`.
Returns `{ sessionToken, expiresAt, user: { id, displayName } }`. The
`sessionToken` is a 30-day HS256 JWT minted by this service — present it as
`Authorization: Bearer <sessionToken>` on subsequent calls. Missing/invalid
tokens on any endpoint below just mean "no `userId`" — the calculator itself
stays public; auth-required endpoints return `401` explicitly.

### Calculation History (auth required)
```bash
GET    /v1/calculations?limit=20
GET    /v1/calculations/{id}
DELETE /v1/calculations/{id}
```
Newest-first list of saved sessions (denormalized summaries), full session
detail (request + response), and hard-delete.

### RSU Grants (auth required)
```bash
GET    /v1/grants
POST   /v1/grants
PUT    /v1/grants/{id}
DELETE /v1/grants/{id}
```
Sync equity grants used to project future vesting income.

### Household Budget (auth required)
```bash
GET    /v1/budget
PUT    /v1/budget
DELETE /v1/budget
```
Single object per user (savings goals + itemized expenses) — not a list.
`PUT` replaces the whole thing.

### AI Budget Plan
```bash
POST /v1/budget/plan
```
Auth-optional (mirrors `/v1/calculate` — a not-yet-saved budget can still get
a plan preview). Sends goals/expenses/cadence/windfalls to Gemini via Vertex
AI and returns a structured per-goal contribution strategy + warnings.
Returns `503` when Vertex AI is unconfigured or unreachable — the iOS client
falls back to its own deterministic on-device engine.

### Stock Search / Quote (auth required)
```bash
GET /v1/stocks/search?q=
GET /v1/stocks/quote/{symbol}
```
Finnhub proxy for RSU grant pricing. Auth-gated to protect the upstream API
key from anonymous farming. `503` when Finnhub is unconfigured/down; unknown
symbol returns `404`.

### Account
```bash
DELETE /v1/account
```
Purges the user's calculation history, grants, and budget.

### Health / Metadata
```bash
GET /v1/health
GET /actuator/health
GET /actuator/prometheus
GET /v1/countries
GET /v1/countries/US/states
GET /v1/tax-years
```

### API Documentation
The interactive Swagger UI was removed during the Javalin migration. DTOs still carry
`swagger-annotations`, so an OpenAPI spec can be re-introduced later. Until then, use the
endpoint list above and the usage examples below.

## 📖 API Usage Examples

### US Salary Calculation (Basic)

**Request:**
```bash
curl -X POST http://localhost:8080/v1/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "country": "US",
    "taxYear": 2025,
    "annualSalary": 100000,
    "countryOptions": {
      "US": {
        "state": "CA",
        "filingStatus": "SINGLE"
      }
    }
  }'
```

**Response:**
```json
{
  "calculationId": "c_a1b2c3d4",
  "grossPerCadence": 100000.0,
  "netPerCadence": 72556.15,
  "currency": "USD",
  "rulePackVersion": "US-2025.11.0",
  "lineItems": [
    {
      "name": "Pre-tax Deductions",
      "amount": 0.0
    },
    {
      "name": "Federal Income Tax",
      "amount": 13841.0
    },
    {
      "name": "State Income Tax",
      "amount": 5952.85
    },
    {
      "name": "Social Security",
      "amount": 6200.0
    },
    {
      "name": "Medicare",
      "amount": 1450.0
    }
  ],
  "explanation": [
    {
      "id": "fed_tax_brackets",
      "text": "Applied 2025 federal tax brackets based on SINGLE"
    },
    {
      "id": "state_tax",
      "text": "Applied CA state tax rates"
    }
  ]
}
```

### US Salary Calculation (With Deductions + Roth 401(k))

**Request:**
```bash
curl -X POST http://localhost:8080/v1/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "country": "US",
    "taxYear": 2025,
    "annualSalary": 120000,
    "cadence": "MONTHLY",
    "pretax": {
      "pensionPercent": 0.06,
      "hsa": 3850
    },
    "posttax": {
      "fixed": 100,
      "roth401kPercent": 0.02
    },
    "countryOptions": {
      "US": {
        "state": "NY",
        "filingStatus": "MARRIED",
        "allowances": 2
      }
    }
  }'
```

**Response:**
```json
{
  "calculationId": "c_e5f6g7h8",
  "grossPerCadence": 10000.0,
  "netPerCadence": 7034.68,
  "currency": "USD",
  "rulePackVersion": "US-2025.11.0",
  "lineItems": [
    {
      "name": "Pre-tax Deductions",
      "amount": 1041.67
    },
    {
      "name": "401(k)",
      "amount": 600.0
    },
    {
      "name": "Federal Income Tax",
      "amount": 785.0
    },
    {
      "name": "State Income Tax",
      "amount": 388.10
    },
    {
      "name": "Social Security",
      "amount": 620.0
    },
    {
      "name": "Medicare",
      "amount": 145.0
    },
    {
      "name": "Post-tax Deductions",
      "amount": 100.0
    },
    {
      "name": "Roth 401(k)",
      "amount": 200.0
    }
  ],
  "explanation": [
    {
      "id": "fed_tax_brackets",
      "text": "Applied 2025 federal tax brackets based on MARRIED"
    },
    {
      "id": "state_tax",
      "text": "Applied NY state tax rates"
    }
  ]
}
```

Note: `401(k)` (Traditional, `pretax.pensionPercent`) reduces taxable income
before Federal/State tax is computed. `Roth 401(k)` (`posttax.roth401kPercent`)
is post-tax federally — it's subtracted from net **after** all taxes, not
before, and applies only to regular wages (never bonus/commission/RSU
vesting).

### UK Salary Calculation (Basic)

**Request:**
```bash
curl -X POST http://localhost:8080/v1/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "country": "UK",
    "taxYear": 2025,
    "annualSalary": 50000
  }'
```

**Response:**
```json
{
  "calculationId": "c_i9j0k1l2",
  "grossPerCadence": 50000.0,
  "netPerCadence": 39519.6,
  "currency": "GBP",
  "rulePackVersion": "UK-2025.4.0",
  "lineItems": [
    {
      "name": "Gross Salary",
      "amount": 50000.0
    },
    {
      "name": "Tax-Free Allowance",
      "amount": -12570.0
    },
    {
      "name": "Taxable Income",
      "amount": 37430.0
    },
    {
      "name": "Income Tax (Basic Rate 20%)",
      "amount": 7486.0
    },
    {
      "name": "Total Income Tax",
      "amount": 7486.0
    },
    {
      "name": "National Insurance (Main Rate 8%)",
      "amount": 2994.4
    },
    {
      "name": "Total National Insurance",
      "amount": 2994.4
    },
    {
      "name": "Net Take-Home Pay",
      "amount": 39519.6
    }
  ],
  "explanation": [
    {
      "id": "basic_rate_tax",
      "text": "Basic rate (20%) on £37430.00"
    },
    {
      "id": "ni_main_rate",
      "text": "8% rate on £37430.00 (between £12570 and £50270)"
    },
    {
      "id": "personal_allowance",
      "text": "Full personal allowance of £12570 applied"
    },
    {
      "id": "tax_code",
      "text": "Tax code 1257L used for calculation"
    }
  ]
}
```

### Get Supported Countries

**Request:**
```bash
curl http://localhost:8080/v1/countries
```

**Response:**
```json
{
  "countries": ["US", "UK"],
  "count": 2
}
```

### Health Check

**Request:**
```bash
curl http://localhost:8080/v1/health
```

**Response:**
```json
{
  "status": "UP",
  "calculators": 2,
  "supportedCountries": 2
}
```

## 📝 Request Body Schema

### Required Fields

| Field | Type | Description | Required |
|-------|------|-------------|----------|
| `country` | string | Country code (US, UK) | Yes |
| `taxYear` | integer | Tax year (>= 2025) | Yes |
| `annualSalary` | number | Annual gross salary (provide this OR `earnings`) | Yes |
| `countryOptions.US.state` | string | US state code | Yes (for US) |
| `countryOptions.US.filingStatus` | string | SINGLE, MARRIED, or HEAD_OF_HOUSEHOLD | Yes (for US) |

### Optional Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `cadence` | string | ANNUAL | Pay frequency: ANNUAL, SEMIANNUAL, QUARTERLY, MONTHLY, SEMIMONTHLY, BIWEEKLY, WEEKLY, DAILY |
| `bonus` | number | 0.0 | Annual bonus / supplemental wages (US: flat 22% federal withholding) |
| `earnings` | object | null | Structured earnings (`salary` or `hourly` + bonus/commission/RSU vesting, dated/recurring bonus). Alternative to `annualSalary` |
| `payDate` | string | null | Pay date (ISO-8601 yyyy-MM-dd). Informational only |
| `pretax.percent` | number | 0.0 | Percentage-based pre-tax deduction (0-1) |
| `pretax.pensionPercent` | number | 0.0 | Traditional pension / 401(k) contribution percent (0-1) — pre-tax |
| `pretax.fixed` | number | 0.0 | Fixed pre-tax deduction (catch-all); use `customDeductions` for named items |
| `pretax.hsa` | number | 0.0 | HSA contribution (US only) |
| `pretax.medical` | number | 0.0 | Annual medical insurance premium (US only) |
| `pretax.dental` | number | 0.0 | Annual dental insurance premium (US only) |
| `pretax.vision` | number | 0.0 | Annual vision insurance premium (US only) |
| `pretax.healthcareFsa` | number | 0.0 | Annual Healthcare FSA contribution (US only) |
| `pretax.dependentCareFsa` | number | 0.0 | Annual Dependent Care FSA contribution (US only, separate $5k IRS cap) |
| `pretax.customDeductions` | array | `[]` | Named custom pre-tax deductions: `[{ "name": "...", "amount": ... }]` |
| `posttax.fixed` | number | 0.0 | Fixed post-tax deduction amount |
| `posttax.roth401kPercent` | number | 0.0 | Roth 401(k) employee contribution percent of regular wages (0-1, US only). Post-tax federally, subtracted from net after all taxes |
| `posttax.studentLoanPlan` | string | null | Student loan plan: PLAN1, PLAN2, POSTGRAD (UK only) |
| `countryOptions.US.allowances` | integer | 0 | Number of allowances (legacy / pre-2020 W-4, used when `w4.useOldW4` is true) |
| `countryOptions.US.w4` | object | null | Modern W-4 fields (2020+) — see W-4 Schema below |
| `countryOptions.UK.taxCode` | string | "1257L" | UK tax code |
| `countryOptions.UK.scottishResident` | boolean | false | Scottish resident flag |
| `countryOptions.UK.niCategory` | string | "A" | National Insurance category |

### W-4 Schema (`countryOptions.US.w4`)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `useOldW4` | boolean | false | Use 2019-or-earlier W-4 (allowances-based, subtracts `allowances × withholdingAllowance` from taxable income) instead of the modern W-4 |
| `dependentsAmount` | number | 0.0 | Step 3 dependents credit ($2000/qualifying child + $500/other dependent). Modern W-4 only |
| `otherIncome` | number | 0.0 | Step 4(a) — annual non-job income. Modern W-4 only |
| `itemizedDeductions` | number | 0.0 | Step 4(b) — itemized deductions (overrides standard deduction if greater). Modern W-4 only |
| `additionalWithholding` | number | 0.0 | Step 4(c) — additional federal withholding **per pay period**. Both W-4 paths |
| `exemptFederal` | boolean | false | Exempt from federal income tax withholding |
| `exemptSocialSecurity` | boolean | false | Exempt from Social Security tax |
| `exemptMedicare` | boolean | false | Exempt from Medicare tax (also disables additional Medicare surtax) |

## 🧪 Testing

See [TESTING.md](TESTING.md) for full testing documentation.

```bash
# Run all unit tests, all modules
./gradlew test

# Run tests for one module
./gradlew :modules:calculator:test

# Full quality gate — tests, checkstyle, spotbugs, JaCoCo ≥80% coverage
./gradlew build
```

## 🔧 Development

### Adding a New Country

1. Create calculator in `modules/calculator/src/main/java/app/salary/calculator/countries/`
2. Implement `CountryCalculator` interface (take any shared calculators via the constructor)
3. Create rule pack JSON in `modules/rules-registry/src/main/resources/rulepacks/`
4. Register it: add `new YourCalculator(...)` to the `calculators` list wired into `CalculatorRegistry` in `modules/api/src/main/java/app/salary/api/Main.java`

See existing calculators (`USCalculator`, `UKCalculator`) for examples.

## 📊 Monitoring

- Health endpoint: `GET /v1/health` (also `GET /actuator/health`)
- Prometheus metrics: `GET /actuator/prometheus`
- Optional Prometheus + Grafana stack: `docker compose --profile monitoring up`
- `X-Request-Id` correlation header, propagated across service-to-service calls, tied into every log line via SLF4J/Logback MDC

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Ensure the full gate passes: `./gradlew build`
5. Submit a pull request

All PRs are automatically validated via GitHub Actions (tests, Checkstyle, SpotBugs, SonarQube Cloud).

## 📄 License

This project is licensed under the MIT License.
