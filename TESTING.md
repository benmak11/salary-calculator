# Testing Documentation

## Overview
This project includes comprehensive unit tests and integration tests to ensure code quality and functionality.

## Running Tests

### Run All Tests
```bash
./gradlew test integrationTest
```

### Run Unit Tests Only
```bash
./gradlew test
```

### Run Integration Tests Only
```bash
./gradlew integrationTest
```

### Run Tests for Specific Module
```bash
./gradlew :modules:calculator:test
./gradlew :modules:common:test
```

## Test Coverage

### Unit Tests (40 tests)

#### CountryOptionsValidator Tests (10 tests)
- Validates location-aware required fields
- Ensures US requires state and filingStatus
- Ensures UK has optional fields with defaults
- **Location**: `modules/common/src/test/java/app/salary/common/validation/CountryOptionsValidatorTest.java`

#### CalculationInput Tests (8 tests)
- Tests mapping from CalculateRequest to CalculationInput
- Validates default value handling
- Tests all country option mappings
- **Location**: `modules/calculator/src/test/java/app/salary/calculator/engine/CalculationInputTest.java`

#### USCalculator Tests (11 tests)
- Tests US salary calculations
- Validates federal and state tax calculations
- Tests FICA and Medicare calculations
- Validates high-income scenarios
- **Location**: `modules/calculator/src/test/java/app/salary/calculator/countries/USCalculatorTest.java`

#### UKCalculator Tests (11 tests)
- Tests UK salary calculations
- Validates income tax and National Insurance
- Tests personal allowance tapering
- Validates pension and student loan deductions
- **Location**: `modules/calculator/src/test/java/app/salary/calculator/countries/UKCalculatorTest.java`

### Integration Tests (20 tests)

#### Calculate Endpoint Tests (15 tests)
- Valid US and UK requests
- Different filing statuses (SINGLE, MARRIED)
- Pretax and posttax deductions
- Different pay cadences (ANNUAL, MONTHLY)
- Validation error scenarios
- High income calculations
- State-specific rules
- **Location**: `modules/integration-tests/src/test/java/app/salary/integration/CalculateEndpointIntegrationTest.java`

#### Countries Endpoint Tests (3 tests)
- Returns list of supported countries
- Validates response structure
- Ensures US and UK are included
- **Location**: `modules/integration-tests/src/test/java/app/salary/integration/CountriesEndpointIntegrationTest.java`

#### Health Endpoint Tests (2 tests)
- Returns UP status
- Reports number of calculators
- **Location**: `modules/integration-tests/src/test/java/app/salary/integration/HealthEndpointIntegrationTest.java`

## Code Coverage Exclusions

The following classes are excluded from code coverage requirements using `@ExcludeFromCodeCoverage`:

### DTOs (Data Transfer Objects)
- `CalculateRequest`
- `CalculateResponse`
- `Income`
- `Pretax`
- `Posttax`
- `CountryOptions`
- `CountryOptionsUS`
- `CountryOptionsUK`
- `LineItem`
- `Explanation`

### Exception Handlers
- `GlobalExceptionHandler`

These classes are excluded because they are simple data containers with no business logic.

## CI/CD Integration

### GitHub Actions Workflow

The project includes a comprehensive CI/CD pipeline (`.github/workflows/ci.yml`) that runs on:
- Push to `main` branch
- Pull requests to `main` branch

#### Workflow Jobs

**1. Test Job**
- Builds the project
- Runs unit tests
- Runs integration tests
- Publishes test results

**2. Lint Job**
- Runs code quality checks
- Performs static analysis

**3. Build Docker Job** (only on main branch push)
- Builds the application JAR
- Creates Docker image (if Dockerfile exists)

## Test Categories

### API Contract Tests
Integration tests verify the API contract by testing:
- Request/response structure
- HTTP status codes
- JSON schema validation
- Error message formats

### Business Logic Tests
Unit tests verify business logic:
- Tax calculations
- Deduction handling
- Cadence conversions
- Country-specific rules

### Validation Tests
Both unit and integration tests verify:
- Required field validation
- Data type validation
- Business rule validation
- Error handling

## Best Practices

1. **Run tests before committing**: Always run `./gradlew test integrationTest` before committing
2. **Write tests for new features**: All new features should include corresponding tests
3. **Maintain test coverage**: Aim for high test coverage on business logic
4. **Use descriptive test names**: Test names should clearly describe what they're testing
5. **Keep tests independent**: Tests should not depend on each other
6. **Mock external dependencies**: Integration tests use MockMvc to avoid external dependencies

## Troubleshooting

### Integration Tests Failing
If integration tests fail:
1. Ensure Spring Boot application can start
2. Check that all required dependencies are in the classpath
3. Verify rule pack JSON files are present in resources

### Unit Tests Failing
If unit tests fail:
1. Check that mock objects are properly configured
2. Verify test data matches expected business rules
3. Ensure no hardcoded values that might change

## Future Improvements

- [ ] Add code coverage reporting with JaCoCo
- [ ] Add mutation testing with PIT
- [ ] Add contract testing with Pact
- [ ] Add performance/load testing
- [ ] Add E2E tests for complete user workflows
