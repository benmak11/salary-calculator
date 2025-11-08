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
class CountriesEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getCountries_shouldReturnSupportedCountries() throws Exception {
        mockMvc.perform(get("/v1/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countries", notNullValue()))
                .andExpect(jsonPath("$.countries", isA(java.util.List.class)))
                .andExpect(jsonPath("$.countries", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.count", notNullValue()))
                .andExpect(jsonPath("$.count", greaterThan(0)));
    }

    @Test
    void getCountries_shouldIncludeUSAndUK() throws Exception {
        mockMvc.perform(get("/v1/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countries", hasItem("US")))
                .andExpect(jsonPath("$.countries", hasItem("UK")))
                .andExpect(jsonPath("$.count", is(2)));
    }

    @Test
    void getCountries_countShouldMatchArraySize() throws Exception {
        mockMvc.perform(get("/v1/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countries.length()", is(2)))
                .andExpect(jsonPath("$.count", is(2)));
    }
}
