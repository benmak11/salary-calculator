# Salary Calculator Microservice

A production-ready Javalin microservice for calculating net pay (take-home salary) with detailed tax breakdowns for multiple countries.

## 🌟 Features

- 🌍 **Multi-country support** (US, UK - easily extensible)
- 💰 **Multiple pay cadences** (annual, monthly, biweekly, weekly)
- 📊 **Detailed tax breakdown** by bands
- 📝 **Human-readable explanations** for each calculation
- 🔄 **Pluggable** country calculators (registered in `Main.java`)
- 🚀 **Fast development** - add new country in 15 minutes
- 📦 **Shared utilities** for code reuse
- 🐳 **Docker ready**
- 📈 **Production monitoring** (Prometheus, health checks)

## 🏗️ Architecture

### Unified Calculator Module
- All country calculators in one module
- Shared utilities for common logic
- Plain constructor injection (no DI framework)
- Calculators registered explicitly in `Main.java` and resolved at runtime by `CalculatorRegistry`

### Supported Countries
- 🇺🇸 **United States** (Federal + State taxes, FICA, Medicare)
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
# Build everything (compiles all modules, runs remaining unit tests)
./gradlew clean build

# Run the API on :8080 — via the Gradle `application` plugin's `run` task
# (replaces Spring Boot's `bootRun`)
./gradlew :modules:api:run

# Optionally run the rule-pack-service on :8081 in another terminal.
# ENABLE_GCP=false boots it without Firestore/GCS/Pub-Sub, so the API
# transparently falls back to its embedded classpath rule pack.
ENABLE_GCP=false ./gradlew :modules:rule-pack-service:run
```

Or run the packaged fat JARs (built with `shadowJar`, replaces `bootJar`):
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

## 📡 API Endpoints

### Calculate Salary
```bash
POST /v1/calculate
```

### Health Check
```bash
GET /v1/health
```

### List Supported Countries
```bash
GET /v1/countries
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
  "rulePackVersion": "US-2025.10.0",
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
      "name": "FICA (Social Security)",
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

### US Salary Calculation (With Deductions)

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
      "fixed": 100
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
  "netPerCadence": 7234.68,
  "currency": "USD",
  "rulePackVersion": "US-2025.10.0",
  "lineItems": [
    {
      "name": "Pre-tax Deductions",
      "amount": 1041.67
    },
    {
      "name": "Federal Income Tax",
      "amount": 835.42
    },
    {
      "name": "State Income Tax",
      "amount": 412.56
    },
    {
      "name": "FICA (Social Security)",
      "amount": 620.0
    },
    {
      "name": "Medicare",
      "amount": 145.0
    },
    {
      "name": "Post-tax Deductions",
      "amount": 100.0
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

### UK Salary Calculation (With Custom Tax Code)

**Request:**
```bash
curl -X POST http://localhost:8080/v1/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "country": "UK",
    "taxYear": 2025,
    "annualSalary": 60000,
    "cadence": "MONTHLY",
    "pretax": {
      "pensionPercent": 0.05
    },
    "countryOptions": {
      "UK": {
        "taxCode": "1100L",
        "scottishResident": false,
        "niCategory": "A"
      }
    }
  }'
```

**Response:**
```json
{
  "calculationId": "c_m3n4o5p6",
  "grossPerCadence": 5000.0,
  "netPerCadence": 3793.12,
  "currency": "GBP",
  "rulePackVersion": "UK-2025.4.0",
  "lineItems": [
    {
      "name": "Gross Salary",
      "amount": 5000.0
    },
    {
      "name": "Tax-Free Allowance",
      "amount": -917.0
    },
    {
      "name": "Taxable Income",
      "amount": 4083.0
    },
    {
      "name": "Income Tax (Basic Rate 20%)",
      "amount": 816.6
    },
    {
      "name": "Total Income Tax",
      "amount": 816.6
    },
    {
      "name": "National Insurance (Main Rate 8%)",
      "amount": 326.64
    },
    {
      "name": "Total National Insurance",
      "amount": 326.64
    },
    {
      "name": "Employee Pension Contribution",
      "amount": 250.0
    },
    {
      "name": "Net Take-Home Pay",
      "amount": 3793.12
    }
  ],
  "explanation": [
    {
      "id": "basic_rate_tax",
      "text": "Basic rate (20%) on £4083.00"
    },
    {
      "id": "ni_main_rate",
      "text": "8% rate on £4083.00 (between £1047.50 and £4189.17)"
    },
    {
      "id": "pension_contribution",
      "text": "Employee contribution: 5.0% of gross salary (£250.00). Employer minimum contribution: 3% (£150.00)"
    },
    {
      "id": "personal_allowance",
      "text": "Full personal allowance of £11000 applied"
    },
    {
      "id": "tax_code",
      "text": "Tax code 1100L used for calculation"
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
| `annualSalary` | number | Annual gross salary | Yes |
| `countryOptions.US.state` | string | US state code | Yes (for US) |
| `countryOptions.US.filingStatus` | string | SINGLE or MARRIED | Yes (for US) |

### Optional Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `cadence` | string | ANNUAL | Pay frequency: ANNUAL, MONTHLY, BIWEEKLY, WEEKLY |
| `pretax.pensionPercent` | number | 0.0 | Pre-tax pension contribution (0-1) |
| `pretax.fixed` | number | 0.0 | Fixed pre-tax deduction amount |
| `pretax.hsa` | number | 0.0 | HSA contribution (US only) |
| `posttax.fixed` | number | 0.0 | Fixed post-tax deduction amount |
| `posttax.studentLoanPlan` | string | null | Student loan plan: PLAN1, PLAN2, POSTGRAD (UK only) |
| `countryOptions.US.allowances` | integer | 0 | Number of allowances (US only) |
| `countryOptions.UK.taxCode` | string | "1257L" | UK tax code |
| `countryOptions.UK.scottishResident` | boolean | false | Scottish resident flag |
| `countryOptions.UK.niCategory` | string | "A" | National Insurance category |

## 🧪 Testing

See [TESTING.md](TESTING.md) for comprehensive testing documentation.

```bash
# Run all tests
./gradlew test integrationTest

# Run only unit tests
./gradlew test

# Run only integration tests
./gradlew integrationTest
```

## 🔧 Development

### Adding a New Country

1. Create calculator in `modules/calculator/src/main/java/app/salary/calculator/countries/`
2. Implement `CountryCalculator` interface (take any shared calculators via the constructor)
3. Create rule pack JSON in `modules/rules-registry/src/main/resources/rulepacks/`
4. Register it: add `new YourCalculator(...)` to the `calculators` list wired into `CalculatorRegistry` in `modules/api/src/main/java/app/salary/api/Main.java`

See existing calculators (USCalculator, UKCalculator) for examples.

## 📊 Monitoring

- Health endpoint: `GET /v1/health` (also `GET /actuator/health`)
- Prometheus metrics: `GET /actuator/prometheus`
- Optional Prometheus + Grafana stack: `docker compose --profile monitoring up`

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Ensure all tests pass: `./gradlew test integrationTest`
5. Submit a pull request

All PRs are automatically validated via GitHub Actions CI/CD pipeline.

## 📄 License

This project is licensed under the MIT License.
