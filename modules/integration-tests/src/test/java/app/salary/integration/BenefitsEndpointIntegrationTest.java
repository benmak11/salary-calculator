package app.salary.integration;

import app.salary.api.SalaryCalculatorApplication;
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
class BenefitsEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getBenefitPlans_shouldReturnThreeHealthPlans() throws Exception {
        mockMvc.perform(get("/v1/benefits/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planYear",         is(2024)))
                .andExpect(jsonPath("$.enrollmentStatus", is("ACTIVE")))
                .andExpect(jsonPath("$.health",           hasSize(3)))
                .andExpect(jsonPath("$.health[?(@.type == 'MEDICAL')]", hasSize(1)))
                .andExpect(jsonPath("$.health[?(@.type == 'DENTAL')]",  hasSize(1)))
                .andExpect(jsonPath("$.health[?(@.type == 'VISION')]",  hasSize(1)));
    }

    @Test
    void getEmployerMatch_shouldReturnDefaults() throws Exception {
        mockMvc.perform(get("/v1/benefits/employer-match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualMatchAmount",    is(4200.0)))
                .andExpect(jsonPath("$.matchPercent",         is(100.0)))
                .andExpect(jsonPath("$.matchThresholdPercent",is(6.0)))
                .andExpect(jsonPath("$.matchDescription",     notNullValue()));
    }

    @Test
    void getImpactAnalysis_withUnknownId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/v1/benefits/impact-analysis/c_nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getImpactAnalysis_afterCalculation_shouldReturnBreakdown() throws Exception {
        // First, perform a calculation to populate the store
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

        // Extract calculationId
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String calcId = mapper.readTree(calcResponse).get("calculationId").asText();

        mockMvc.perform(get("/v1/benefits/impact-analysis/" + calcId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationId", is(calcId)))
                .andExpect(jsonPath("$.breakdown",     hasSize(greaterThan(0))));
    }

    @Test
    void getRetirementContributions_shouldReturnRates() throws Exception {
        mockMvc.perform(get("/v1/benefits/retirement/contributions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traditional401k.employeePercent", is(8.0)))
                .andExpect(jsonPath("$.roth401k.employeePercent",        is(4.0)))
                .andExpect(jsonPath("$.totalContributionPercent",        is(12.0)));
    }

    @Test
    void updateRetirementContribution_withValidRequest_shouldReturn204() throws Exception {
        mockMvc.perform(put("/v1/benefits/retirement/contribution")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "type": "TRADITIONAL_401K", "employeePercent": 10.0 }
                    """))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateRetirementContribution_withInvalidType_shouldReturn400() throws Exception {
        mockMvc.perform(put("/v1/benefits/retirement/contribution")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "type": "INVALID_TYPE", "employeePercent": 5.0 }
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEnrollmentWindow_shouldReturnOpenStatus() throws Exception {
        mockMvc.perform(get("/v1/benefits/enrollment/window"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status",            is("OPEN")))
                .andExpect(jsonPath("$.daysRemaining",     is(14)))
                .andExpect(jsonPath("$.windowStartDate",   notNullValue()))
                .andExpect(jsonPath("$.effectiveChangeDate", notNullValue()));
    }
}
