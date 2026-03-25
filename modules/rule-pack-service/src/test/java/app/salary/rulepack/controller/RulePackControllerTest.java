package app.salary.rulepack.controller;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.service.RulePackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulePackControllerTest {

    @Mock
    private RulePackService rulePackService;

    private RulePackController controller;

    @BeforeEach
    void setUp() {
        controller = new RulePackController(rulePackService);
    }

    @Test
    void listRulePacks_shouldReturnListResponse() {
        RulePackDto dto = createTestDto();
        Page<RulePackDto> page = new PageImpl<>(List.of(dto));

        when(rulePackService.findRulePacks(eq("US"), eq(2025), eq("PUBLISHED"), any()))
                .thenReturn(page);

        var response = controller.listRulePacks("US", 2025, "PUBLISHED", 0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getRulePacks().size());
        assertEquals(1, response.getBody().getTotal());
    }

    @Test
    void getLatestRulePack_whenFound_shouldReturnRulePack() {
        RulePackDto dto = createTestDto();

        when(rulePackService.findLatest("US", 2025)).thenReturn(dto);

        ResponseEntity<RulePackDto> response = controller.getLatestRulePack("US", 2025);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("US", response.getBody().getCountry());
    }

    @Test
    void getLatestRulePack_whenNotFound_shouldReturn404() {
        when(rulePackService.findLatest("XX", 2025)).thenReturn(null);

        ResponseEntity<RulePackDto> response = controller.getLatestRulePack("XX", 2025);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getRulePack_whenFound_shouldReturnRulePack() {
        RulePackDto dto = createTestDto();

        when(rulePackService.findById("test-id")).thenReturn(Optional.of(dto));

        ResponseEntity<RulePackDto> response = controller.getRulePack("test-id");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void getRulePack_whenNotFound_shouldReturn404() {
        when(rulePackService.findById("nonexistent")).thenReturn(Optional.empty());

        ResponseEntity<RulePackDto> response = controller.getRulePack("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void downloadRulePack_whenFound_shouldReturnContent() {
        Map<String, Object> content = Map.of("rule", "value");

        when(rulePackService.downloadRulePack("test-id")).thenReturn(Optional.of(content));

        ResponseEntity<Map<String, Object>> response = controller.downloadRulePack("test-id");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("value", response.getBody().get("rule"));
    }

    @Test
    void downloadRulePack_whenNotFound_shouldReturn404() {
        when(rulePackService.downloadRulePack("nonexistent")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.downloadRulePack("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createRulePack_shouldCreateAndReturn201() {
        RulePackDto dto = createTestDto();
        Map<String, Object> rulePackJson = Map.of("rule", "value");

        when(rulePackService.createRulePack(
                eq("US"), eq(2025), eq("2025.1.0"), any(LocalDate.class), eq(rulePackJson)))
                .thenReturn(dto);

        ResponseEntity<?> response = controller.createRulePack(
                "US", 2025, "2025.1.0", "2025-01-01", rulePackJson);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createRulePack_whenDuplicate_shouldReturn400() {
        Map<String, Object> rulePackJson = Map.of("rule", "value");

        when(rulePackService.createRulePack(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Duplicate exists"));

        ResponseEntity<?> response = controller.createRulePack(
                "US", 2025, "2025.1.0", "2025-01-01", rulePackJson);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createRulePack_whenError_shouldReturn500() {
        Map<String, Object> rulePackJson = Map.of("rule", "value");

        when(rulePackService.createRulePack(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        ResponseEntity<?> response = controller.createRulePack(
                "US", 2025, "2025.1.0", "2025-01-01", rulePackJson);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void publishRulePack_shouldReturnPublishedRulePack() {
        RulePackDto dto = createTestDto();
        dto.setStatus(RulePackStatus.PUBLISHED);

        when(rulePackService.publishRulePack("test-id")).thenReturn(dto);

        ResponseEntity<?> response = controller.publishRulePack("test-id");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void publishRulePack_whenNotFound_shouldReturn404() {
        when(rulePackService.publishRulePack("nonexistent"))
                .thenThrow(new RuntimeException("Not found"));

        ResponseEntity<?> response = controller.publishRulePack("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deprecateRulePack_shouldReturnDeprecatedRulePack() {
        RulePackDto dto = createTestDto();
        dto.setStatus(RulePackStatus.DEPRECATED);

        when(rulePackService.deprecateRulePack("test-id")).thenReturn(dto);

        ResponseEntity<?> response = controller.deprecateRulePack("test-id");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void deprecateRulePack_whenNotFound_shouldReturn404() {
        when(rulePackService.deprecateRulePack("nonexistent"))
                .thenThrow(new RuntimeException("Not found"));

        ResponseEntity<?> response = controller.deprecateRulePack("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    private RulePackDto createTestDto() {
        RulePackDto dto = new RulePackDto();
        dto.setId("test-id");
        dto.setCountry("US");
        dto.setTaxYear(2025);
        dto.setVersion("2025.1.0");
        dto.setStatus(RulePackStatus.PUBLISHED);
        dto.setEffectiveDate(LocalDate.of(2025, 1, 1));
        dto.setCreatedAt(LocalDateTime.now());
        dto.setCreatedBy("system");
        return dto;
    }
}
