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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SalaryCalculatorApplication.class)
@AutoConfigureMockMvc
class CalculateEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── existing scenarios ────────────────────────────────────────────────────────

    @Test
    void calculate_withValidUSRequest_shouldReturnCalculation() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "countryOptions": {
                    "US": { "state": "CA", "filingStatus": "SINGLE" }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculationId", notNullValue()))
                .andExpect(jsonPath("$.grossPerCadence", is(100000.0)))
                .andExpect(jsonPath("$.baseSalaryPerCadence", is(100000.0)))
                .andExpect(jsonPath("$.bonusPerCadence", is(0.0)))
                .andExpect(jsonPath("$.netPerCadence", greaterThan(0.0)))
                .andExpect(jsonPath("$.netPerCadence", lessThan(100000.0)))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.lineItems", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Federal Income Tax')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'State Income Tax')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'FICA (Social Security)')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Medicare')]", hasSize(1)));
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
                .andExpect(jsonPath("$.currency", is("GBP")))
                .andExpect(jsonPath("$.grossPerCadence", is(50000.0)))
                .andExpect(jsonPath("$.netPerCadence", greaterThan(0.0)));
    }

    @Test
    void calculate_withUSMarriedFilingStatus_shouldReturnCalculation() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 150000,
                "countryOptions": {
                    "US": { "state": "MD", "filingStatus": "MARRIED", "allowances": 2 }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossPerCadence", is(150000.0)))
                .andExpect(jsonPath("$.currency", is("USD")));
    }

    // ── HEAD_OF_HOUSEHOLD ──────────────────────────────────────────────────────────

    @Test
    void calculate_withHeadOfHouseholdFilingStatus_shouldUseHoHBracketsAndDeduction() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 80000,
                "countryOptions": {
                    "US": { "state": "TX", "filingStatus": "HEAD_OF_HOUSEHOLD" }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.grossPerCadence", is(80000.0)))
                .andExpect(jsonPath("$.netPerCadence", greaterThan(0.0)))
                .andExpect(jsonPath("$.netPerCadence", lessThan(80000.0)))
                .andExpect(jsonPath("$.explanation[?(@.id == 'fed_tax_brackets')]", hasSize(1)));
    }

    @Test
    void calculate_hohTaxShouldBeLessThanSingleForSameIncome() throws Exception {
        String singleRequest = """
            {
                "country": "US", "taxYear": 2025, "annualSalary": 80000,
                "countryOptions": { "US": { "state": "TX", "filingStatus": "SINGLE" } }
            }
            """;
        String hohRequest = """
            {
                "country": "US", "taxYear": 2025, "annualSalary": 80000,
                "countryOptions": { "US": { "state": "TX", "filingStatus": "HEAD_OF_HOUSEHOLD" } }
            }
            """;

        String singleResult = mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON).content(singleRequest))
                .andReturn().getResponse().getContentAsString();
        String hohResult = mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON).content(hohRequest))
                .andReturn().getResponse().getContentAsString();

        double singleNet = objectMapper.readTree(singleResult).get("netPerCadence").asDouble();
        double hohNet    = objectMapper.readTree(hohResult).get("netPerCadence").asDouble();

        // HoH has higher standard deduction ($21,900 vs $14,600), so net pay must be higher
        assertTrue(hohNet > singleNet,
            "HoH net (" + hohNet + ") should exceed SINGLE net (" + singleNet + ")");
    }

    // ── bonus / supplemental withholding ─────────────────────────────────────────

    @Test
    void calculate_withBonus_shouldIncludeBonusWithholdingExplanation() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 90000,
                "bonus": 10000,
                "countryOptions": {
                    "US": { "state": "CA", "filingStatus": "SINGLE" }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossPerCadence", is(100000.0)))
                .andExpect(jsonPath("$.baseSalaryPerCadence", is(90000.0)))
                .andExpect(jsonPath("$.bonusPerCadence", is(10000.0)))
                .andExpect(jsonPath("$.explanation[?(@.id == 'bonus_withholding')]", hasSize(1)));
    }

    @Test
    void calculate_withBonusInHighBracket_netShouldExceedSalaryOnlyAtSameTotal() throws Exception {
        // At $200k gross, the $10k marginal bracket is 24% for SINGLE filers.
        // With $190k base + $10k bonus: the bonus is withheld at flat 22% < 24% marginal.
        // Therefore net pay with the bonus split should exceed net with $200k base alone.
        String withBonus = """
            {
                "country": "US", "taxYear": 2025, "annualSalary": 190000, "bonus": 10000,
                "countryOptions": { "US": { "state": "TX", "filingStatus": "SINGLE" } }
            }
            """;
        String withoutBonus = """
            {
                "country": "US", "taxYear": 2025, "annualSalary": 200000,
                "countryOptions": { "US": { "state": "TX", "filingStatus": "SINGLE" } }
            }
            """;

        String bonusResult = mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON).content(withBonus))
                .andReturn().getResponse().getContentAsString();
        String noBonusResult = mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON).content(withoutBonus))
                .andReturn().getResponse().getContentAsString();

        double bonusNet  = objectMapper.readTree(bonusResult).get("netPerCadence").asDouble();
        double noBonus_Net = objectMapper.readTree(noBonusResult).get("netPerCadence").asDouble();

        assertTrue(bonusNet > noBonus_Net,
            "Flat 22% bonus withholding should yield higher net than marginal 24% bracket at $200k gross");
    }

    // ── individual benefit line items ─────────────────────────────────────────────

    @Test
    void calculate_withIndividualBenefitFields_shouldEmitNamedLineItems() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "pretax": {
                    "medical": 2184.0,
                    "dental":  510.0,
                    "vision":  288.0
                },
                "countryOptions": {
                    "US": { "state": "CA", "filingStatus": "SINGLE" }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Medical Premium')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Dental Premium')]",  hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Vision Premium')]",  hasSize(1)));
    }

    @Test
    void calculate_withNamedCustomDeductions_shouldEmitEachLineItem() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "pretax": {
                    "customDeductions": [
                        { "name": "Commuter Benefit", "amount": 1200 },
                        { "name": "Parking",          "amount": 600  }
                    ]
                },
                "countryOptions": {
                    "US": { "state": "CA", "filingStatus": "SINGLE" }
                }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Commuter Benefit')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Parking')]",          hasSize(1)));
    }

    // ── existing edge cases ───────────────────────────────────────────────────────

    @Test
    void calculate_withPretaxDeductions_shouldReduceTaxableIncome() throws Exception {
        String requestJson = """
            {
                "country": "US",
                "taxYear": 2025,
                "annualSalary": 100000,
                "pretax": { "pensionPercent": 0.05, "hsa": 3000 },
                "countryOptions": { "US": { "state": "CA", "filingStatus": "SINGLE" } }
            }
            """;

        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Employee 401(k)')]", hasSize(1)));
    }

    @Test
    void calculate_withMonthlyCadence_shouldReturnMonthlyCadence() throws Exception {
        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "country":"UK","taxYear":2025,"annualSalary":60000,"cadence":"MONTHLY" }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossPerCadence", is(5000.0)));
    }

    @Test
    void calculate_withoutUSCountryOptions_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "country":"US","taxYear":2025,"annualSalary":100000 }
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculate_withNullCountry_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "taxYear":2025,"annualSalary":100000 }
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculate_withNegativeSalary_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "country":"UK","taxYear":2025,"annualSalary":-5000 }
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calculate_withHighIncome_shouldCalculateAdditionalMedicare() throws Exception {
        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "country":"US","taxYear":2025,"annualSalary":250000,
                        "countryOptions":{"US":{"state":"CA","filingStatus":"SINGLE"}}
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation[?(@.id == 'additional_medicare')]", hasSize(1)));
    }

    @Test
    void calculate_withTexasState_shouldHaveNoStateTax() throws Exception {
        mockMvc.perform(post("/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "country":"US","taxYear":2025,"annualSalary":100000,
                        "countryOptions":{"US":{"state":"TX","filingStatus":"SINGLE"}}
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineItems[?(@.name == 'Federal Income Tax')]", hasSize(1)))
                .andExpect(jsonPath("$.lineItems[?(@.name == 'State Income Tax')]", hasSize(0)));
    }
}
