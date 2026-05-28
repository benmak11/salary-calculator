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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SalaryCalculatorApplication.class)
@AutoConfigureMockMvc
class InsightsEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getInsight_withUnknownId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/v1/insights/c_nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInsight_afterCalculation_shouldReturnInsight() throws Exception {
        String calcRequest = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "countryOptions": { "US": { "state": "CA", "filingStatus": "SINGLE" } }
            }
            """;

        String calcResponse = mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(calcRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String calcId = objectMapper.readTree(calcResponse).get("calculationId").asText();

        mockMvc.perform(get("/v1/insights/" + calcId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type",                       is("RETIREMENT_OPTIMIZATION")))
                .andExpect(jsonPath("$.headline",                   notNullValue()))
                .andExpect(jsonPath("$.monthlyTaxSavings",          greaterThan(0.0)))
                .andExpect(jsonPath("$.annualRetirementBenefit",    greaterThan(0.0)))
                .andExpect(jsonPath("$.recommendedContributionPercent", is(7.0)))
                .andExpect(jsonPath("$.body",                       notNullValue()))
                .andExpect(jsonPath("$.ctaLabel",                   is("Optimize 401k")));
    }

    @Test
    void getInsight_annualRetirementBenefitShouldReflect1PercentOfGross() throws Exception {
        // annualSalary = 100,000 → 1% increase = 1,000 → with 100% employer match = 2,000
        String calcRequest = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "countryOptions": { "US": { "state": "TX", "filingStatus": "SINGLE" } }
            }
            """;

        String calcResponse = mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON).content(calcRequest))
                .andReturn().getResponse().getContentAsString();
        String calcId = objectMapper.readTree(calcResponse).get("calculationId").asText();

        mockMvc.perform(get("/v1/insights/" + calcId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualRetirementBenefit", is(2000.0)));
    }
}
