package app.salary.integration;

import app.salary.api.SalaryCalculatorApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SalaryCalculatorApplication.class)
@AutoConfigureMockMvc
class CalculateEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void calculate_withValidUSRequest_shouldReturnCalculation() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "countryOptions": {
                    "US": {
                        "state": "CA",
                        "filingStatus": "SINGLE"
                    }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationId", notNullValue()))
                .andExpect(jsonPath("$.grossPerCadence", is(100000.0)))
                .andExpect(jsonPath("$.netPerCadence", greaterThan(0.0)))
                .andExpect(jsonPath("$.netPerCadence", lessThan(100000.0)))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.rulePackVersion", is("US-2025.10.0")))
                .andExpect(jsonPath("$.lineItems", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Federal Income Tax')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'State Income Tax')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'FICA (Social Security)')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Medicare')]", hasSize(1)))
                .andExpect(jsonPath("$.explanation", hasSize(greaterThan(0))));
    }

    @Test
    void calculate_withValidUKRequest_shouldReturnCalculation() throws Exception {
        String requestJson = """
            {
                "country": "UK",
                "taxYear": 2025,
                "annualSalary": 50000
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationId", notNullValue()))
                .andExpect(jsonPath("$.grossPerCadence", is(50000.0)))
                .andExpect(jsonPath("$.netPerCadence", greaterThan(0.0)))
                .andExpect(jsonPath("$.netPerCadence", lessThan(50000.0)))
                .andExpect(jsonPath("$.currency", is("GBP")))
                .andExpect(jsonPath("$.rulePackVersion", is("UK-2025.4.0")))
                .andExpect(jsonPath("$.lineItems", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Total Income Tax')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Total National Insurance')]", hasSize(1)))
                .andExpect(jsonPath("$.explanation", hasSize(greaterThan(0))));
    }

    @Test
    void calculate_withUSMarriedFilingStatus_shouldReturnCalculation() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 150000,
                "countryOptions": {
                    "US": {
                        "state": "MD",
                        "filingStatus": "MARRIED",
                        "allowances": 2
                    }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossPerCadence", is(150000.0)))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.explanation[?(@.id == 'fed_tax_brackets')]", hasSize(1)));
    }

    @Test
    void calculate_withPretaxDeductions_shouldReduceTaxableIncome() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "pretax": {
                    "pensionPercent": 0.05,
                    "hsa": 3000
                },
                "countryOptions": {
                    "US": {
                        "state": "CA",
                        "filingStatus": "SINGLE"
                    }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossPerCadence", is(100000.0)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Pre-tax Deductions')]", hasSize(1)));
    }

    @Test
    void calculate_withMonthlyCadence_shouldReturnMonthlyCadence() throws Exception {
        String requestJson = """
            {
                "country": "UK",
                "taxYear": 2025,
                "annualSalary": 60000,
                "cadence": "MONTHLY"
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossPerCadence", is(5000.0))) // 60000 / 12
                .andExpect(jsonPath("$.netPerCadence", greaterThan(0.0)))
                .andExpect(jsonPath("$.netPerCadence", lessThan(5000.0)));
    }

    @Test
    void calculate_withoutUSCountryOptions_shouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.calculateRequest",
                    containsString("US calculations require state and filing status")));
    }

    @Test
    void calculate_withoutState_shouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "countryOptions": {
                    "US": {
                        "filingStatus": "SINGLE"
                    }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['countryOptions.US.state']",
                    containsString("State is required")));
    }

    @Test
    void calculate_withoutFilingStatus_shouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "countryOptions": {
                    "US": {
                        "state": "CA"
                    }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['countryOptions.US.filingStatus']",
                    containsString("Filing status is required")));
    }

    @Test
    void calculate_withNullCountry_shouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "taxYear": 2025,
                "annualSalary": 100000
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.country", notNullValue()));
    }

    @Test
    void calculate_withNegativeSalary_shouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "country": "UK",
                "taxYear": 2025,
                "annualSalary": -5000
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.annualSalary", notNullValue()));
    }

    @Test
    void calculate_withInvalidTaxYear_shouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "country": "UK",
                "taxYear": 2020,
                "annualSalary": 50000
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.taxYear", notNullValue()));
    }

    @Test
    void calculate_withHighIncome_shouldCalculateAdditionalMedicare() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 250000,
                "countryOptions": {
                    "US": {
                        "state": "CA",
                        "filingStatus": "SINGLE"
                    }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossPerCadence", is(250000.0)))
                .andExpect(jsonPath("$.explanation[?(@.id == 'additional_medicare')]", hasSize(1)));
    }

    @Test
    void calculate_withTexasState_shouldHaveNoStateTax() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "countryOptions": {
                    "US": {
                        "state": "TX",
                        "filingStatus": "SINGLE"
                    }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Federal Income Tax')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'State Income Tax')]", hasSize(0)));
    }

    @Test
    void calculate_withUKCustomTaxCode_shouldUseTaxCode() throws Exception {
        String requestJson = """
            {
                "country": "UK",
                "taxYear": 2025,
                "annualSalary": 50000,
                "countryOptions": {
                    "UK": {
                        "taxCode": "1100L",
                        "scottishResident": false,
                        "niCategory": "A"
                    }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation[?(@.id == 'tax_code')]", hasSize(1)));
    }
}
