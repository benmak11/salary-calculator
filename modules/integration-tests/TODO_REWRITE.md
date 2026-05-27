# Integration test rewrite — follow-up

All Spring `MockMvc`-based integration tests were removed during the Javalin migration
(2026-05-25). The endpoint contracts still need coverage; rewrite them using
**`io.javalin:javalin-testtools`** (`JavalinTest.test(...)`), which spins up a real
Javalin instance on an ephemeral port and exercises it with OkHttp.

Tests that need to come back:

| Original file                          | Endpoints covered                                  |
| -------------------------------------- | -------------------------------------------------- |
| `CalculateEndpointIntegrationTest`     | `POST /v1/calculate`, validation failures          |
| `AppEndpointIntegrationTest`           | `GET /v1/app/legal`, `GET /v1/app/version`         |
| `BenefitsEndpointIntegrationTest`      | All `/v1/benefits/*`                               |
| `CountriesEndpointIntegrationTest`     | `GET /v1/countries`, `GET /v1/countries/US/states` |
| `HealthEndpointIntegrationTest`        | `GET /v1/health`, `GET /actuator/health`           |
| `InsightsEndpointIntegrationTest`      | `GET /v1/insights/{calculationId}`                 |

Also dropped per-controller unit tests in `modules/api/src/test/java/app/salary/api/controller/`
— `JavalinTest`-based equivalents should cover most cases in one shot, so the unit/integration
split is no longer worth maintaining.

`HttpRulePackClientTest` was removed too — it mocked `RestTemplate` which is gone. A
rewrite using a mock `HttpClient` (or an in-process Javalin server returning canned
rule-pack JSON) is the right replacement.
