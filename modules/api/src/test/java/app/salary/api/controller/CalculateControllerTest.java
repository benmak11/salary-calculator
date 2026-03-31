package app.salary.api.controller;

import app.salary.api.service.CalculationStore;
import app.salary.calculator.engine.CalculationOrchestrator;
import app.salary.calculator.registry.CalculatorRegistry;
import app.salary.common.constants.Country;
import app.salary.common.constants.PayCadence;
import app.salary.common.dto.CalculateRequest;
import app.salary.common.dto.CalculateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculateControllerTest {

    @Mock
    private CalculationOrchestrator orchestrator;

    @Mock
    private CalculatorRegistry calculatorRegistry;

    @Mock
    private CalculationStore calculationStore;

    private CalculateController controller;

    @BeforeEach
    void setUp() {
        controller = new CalculateController(orchestrator, calculatorRegistry, calculationStore);
    }

    @Test
    void calculate_withValidRequest_shouldReturnOkResponse() {
        CalculateRequest request = createTestRequest();
        CalculateResponse expectedResponse = createTestResponse();

        when(orchestrator.calculate(any(CalculateRequest.class))).thenReturn(expectedResponse);

        ResponseEntity<CalculateResponse> response = controller.calculate(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("c_12345678", response.getBody().getCalculationId());
        verify(calculationStore).store(expectedResponse);
    }

    @Test
    void calculate_shouldStoreResultInCalculationStore() {
        CalculateRequest request = createTestRequest();
        CalculateResponse expectedResponse = createTestResponse();
        when(orchestrator.calculate(any())).thenReturn(expectedResponse);

        controller.calculate(request);

        verify(calculationStore, times(1)).store(expectedResponse);
    }

    @Test
    void calculate_withIllegalArgumentException_shouldReturnBadRequest() {
        CalculateRequest request = createTestRequest();

        when(orchestrator.calculate(any(CalculateRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid country"));

        ResponseEntity<CalculateResponse> response = controller.calculate(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void calculate_withGenericException_shouldReturnInternalServerError() {
        CalculateRequest request = createTestRequest();

        when(orchestrator.calculate(any(CalculateRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        ResponseEntity<CalculateResponse> response = controller.calculate(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void getSupportedCountries_shouldReturnCountriesList() {
        List<Country> countries = Arrays.asList(Country.UK, Country.US);
        when(calculatorRegistry.getSupportedCountries()).thenReturn(countries);

        ResponseEntity<Map<String, Object>> response = controller.getSupportedCountries();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("countries"));
        assertTrue(response.getBody().containsKey("count"));
        assertEquals(2, response.getBody().get("count"));
    }

    @Test
    void getUsStates_shouldReturn51Entries() {
        ResponseEntity<List<Map<String, String>>> response = controller.getUsStates();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(51, response.getBody().size()); // 50 states + DC
        assertTrue(response.getBody().stream().anyMatch(s -> "CA".equals(s.get("code"))));
        assertTrue(response.getBody().stream().anyMatch(s -> "DC".equals(s.get("code"))));
    }

    @Test
    void getUsStates_shouldHaveCodeAndNameFields() {
        ResponseEntity<List<Map<String, String>>> response = controller.getUsStates();

        assertNotNull(response.getBody());
        Map<String, String> firstEntry = response.getBody().get(0);
        assertTrue(firstEntry.containsKey("code"));
        assertTrue(firstEntry.containsKey("name"));
    }

    @Test
    void getSupportedTaxYears_withUSCountry_shouldReturnTaxYears() {
        ResponseEntity<Map<String, Object>> response = controller.getSupportedTaxYears("US");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("US", response.getBody().get("country"));
        assertTrue(response.getBody().containsKey("supportedTaxYears"));
        assertTrue(response.getBody().containsKey("defaultTaxYear"));
    }

    @Test
    void health_shouldReturnHealthStatus() {
        List<Country> countries = Arrays.asList(Country.UK, Country.US);
        when(calculatorRegistry.getCalculatorCount()).thenReturn(2);
        when(calculatorRegistry.getSupportedCountries()).thenReturn(countries);

        ResponseEntity<Map<String, Object>> response = controller.health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals(2, response.getBody().get("calculators"));
        assertEquals(2, response.getBody().get("supportedCountries"));
    }

    private CalculateRequest createTestRequest() {
        CalculateRequest request = new CalculateRequest();
        request.setCountry(Country.UK);
        request.setTaxYear(2025);
        request.setAnnualSalary(50000.0);
        request.setCadence(PayCadence.MONTHLY);
        return request;
    }

    private CalculateResponse createTestResponse() {
        CalculateResponse response = new CalculateResponse();
        response.setCalculationId("c_12345678");
        response.setGrossPerCadence(4166.67);
        response.setNetPerCadence(3333.33);
        response.setCurrency("GBP");
        response.setRulePackVersion("UK-2025.1.0");
        return response;
    }
}
