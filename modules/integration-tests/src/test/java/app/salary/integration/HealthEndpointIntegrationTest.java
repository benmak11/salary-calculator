package app.salary.integration;

import app.salary.api.SalaryCalculatorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SalaryCalculatorApplication.class)
@AutoConfigureMockMvc
class HealthEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_shouldReturnUpStatus() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.calculators", notNullValue()))
                .andExpect(jsonPath("$.calculators", greaterThan(0)))
                .andExpect(jsonPath("$.supportedCountries", notNullValue()))
                .andExpect(jsonPath("$.supportedCountries", greaterThan(0)));
    }

    @Test
    void health_shouldReport2Calculators() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculators", is(2)))
                .andExpect(jsonPath("$.supportedCountries", is(2)));
    }

    @Test
    void health_shouldHaveCorrectStructure() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.calculators", notNullValue()))
                .andExpect(jsonPath("$.supportedCountries", notNullValue()));
    }
}
