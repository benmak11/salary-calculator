package app.salary.rulepack.graphql;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.service.RulePackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulePackGraphQLControllerTest {

    @Mock
    private RulePackService rulePackService;

    private RulePackGraphQLController controller;

    @BeforeEach
    void setUp() {
        controller = new RulePackGraphQLController(rulePackService);
    }

    // ---- Queries ----

    @Test
    void latestRulePack_whenFound_shouldReturnGraphQLResponse() {
        RulePackDto dto = createTestDto();
        when(rulePackService.findLatest("US", 2025)).thenReturn(dto);

        RulePackGraphQL result = controller.latestRulePack("US", 2025);

        assertNotNull(result);
        assertEquals("test-id", result.id());
        assertEquals("US", result.country());
        assertEquals(2025, result.taxYear());
        assertEquals("2025.1.0", result.version());
        assertEquals("PUBLISHED", result.status());
    }

    @Test
    void latestRulePack_whenNotFound_shouldReturnNull() {
        when(rulePackService.findLatest("XX", 2025)).thenReturn(null);

        RulePackGraphQL result = controller.latestRulePack("XX", 2025);

        assertNull(result);
    }

    @Test
    void rulePacks_shouldReturnListOfGraphQLResponses() {
        RulePackDto dto = createTestDto();
        when(rulePackService.findRulePacks(eq("US"), eq(2025), eq("PUBLISHED"), any()))
                .thenReturn(new PageImpl<>(List.of(dto)));

        List<RulePackGraphQL> result = controller.rulePacks("US", 2025, "PUBLISHED", 0, 10);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("US", result.get(0).country());
    }

    @Test
    void rulePacks_withNullArguments_shouldUseDefaults() {
        when(rulePackService.findRulePacks(isNull(), isNull(), eq("PUBLISHED"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        List<RulePackGraphQL> result = controller.rulePacks(null, null, null, null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(rulePackService).findRulePacks(isNull(), isNull(), eq("PUBLISHED"), eq(PageRequest.of(0, 10)));
    }

    @Test
    void rulePack_whenFound_shouldReturnGraphQLResponse() {
        RulePackDto dto = createTestDto();
        when(rulePackService.findById("test-id")).thenReturn(Optional.of(dto));

        RulePackGraphQL result = controller.rulePack("test-id");

        assertNotNull(result);
        assertEquals("test-id", result.id());
    }

    @Test
    void rulePack_whenNotFound_shouldReturnNull() {
        when(rulePackService.findById("nonexistent")).thenReturn(Optional.empty());

        RulePackGraphQL result = controller.rulePack("nonexistent");

        assertNull(result);
    }

    // ---- Mutations ----

    @Test
    void createRulePack_shouldReturnCreatedGraphQLResponse() {
        RulePackDto dto = createTestDto();
        dto.setStatus(RulePackStatus.DRAFT);

        when(rulePackService.createRulePack(eq("US"), eq(2025), eq("2025.1.0"), any(LocalDate.class), any()))
                .thenReturn(dto);

        RulePackGraphQL result = controller.createRulePack("US", 2025, "2025.1.0", "2025-01-01");

        assertNotNull(result);
        assertEquals("US", result.country());
        assertEquals(2025, result.taxYear());
    }

    @Test
    void publishRulePack_shouldReturnPublishedGraphQLResponse() {
        RulePackDto dto = createTestDto();
        dto.setStatus(RulePackStatus.PUBLISHED);

        when(rulePackService.publishRulePack("test-id")).thenReturn(dto);

        RulePackGraphQL result = controller.publishRulePack("test-id");

        assertNotNull(result);
        assertEquals("PUBLISHED", result.status());
        verify(rulePackService).publishRulePack("test-id");
    }

    @Test
    void deprecateRulePack_shouldReturnDeprecatedGraphQLResponse() {
        RulePackDto dto = createTestDto();
        dto.setStatus(RulePackStatus.DEPRECATED);

        when(rulePackService.deprecateRulePack("test-id")).thenReturn(dto);

        RulePackGraphQL result = controller.deprecateRulePack("test-id");

        assertNotNull(result);
        assertEquals("DEPRECATED", result.status());
        verify(rulePackService).deprecateRulePack("test-id");
    }

    @Test
    void toGraphQL_shouldMapDatesAsIsoStrings() {
        RulePackDto dto = createTestDto();
        dto.setEffectiveDate(LocalDate.of(2025, 1, 1));
        dto.setCreatedAt(LocalDateTime.of(2025, 1, 1, 12, 0, 0));

        when(rulePackService.findLatest("US", 2025)).thenReturn(dto);

        RulePackGraphQL result = controller.latestRulePack("US", 2025);

        assertNotNull(result.effectiveDate());
        assertEquals("2025-01-01", result.effectiveDate());
        assertNotNull(result.createdAt());
        assertTrue(result.createdAt().contains("2025-01-01"));
    }

    @Test
    void toGraphQL_whenNullDates_shouldReturnNullDateFields() {
        RulePackDto dto = createTestDto();
        dto.setEffectiveDate(null);
        dto.setCreatedAt(null);

        when(rulePackService.findLatest("US", 2025)).thenReturn(dto);

        RulePackGraphQL result = controller.latestRulePack("US", 2025);

        assertNull(result.effectiveDate());
        assertNull(result.createdAt());
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
