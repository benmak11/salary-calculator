package app.salary.rulepack.graphql;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.service.RulePackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RulePackGraphQLControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private RulePackService service;

    @BeforeEach
    void setUp() {
        service = mock(RulePackService.class);
    }

    private Javalin app() {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            new RulePackGraphQLController(service, MAPPER).register(config.routes);
        });
    }

    private RulePackDto dto(String id, String country, int year, String version, RulePackStatus status) {
        return RulePackDto.builder()
                .id(id).country(country).taxYear(year).version(version).status(status)
                .effectiveDate(LocalDate.of(year, 1, 1))
                .createdAt(LocalDateTime.of(year, 1, 1, 0, 0))
                .build();
    }

    @Test
    void query_latestRulePack_returnsData() {
        when(service.findLatest("US", 2025))
                .thenReturn(dto("id-1", "US", 2025, "US-2025.11.0", RulePackStatus.PUBLISHED));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/graphql", Map.of(
                    "query", "{ latestRulePack(country: \"US\", taxYear: 2025) { id version status } }"
            ));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            JsonNode pack = body.get("data").get("latestRulePack");
            assertEquals("id-1",          pack.get("id").asText());
            assertEquals("US-2025.11.0",  pack.get("version").asText());
            assertEquals("PUBLISHED",     pack.get("status").asText());
        });
    }

    @Test
    void query_latestRulePack_whenMissing_returnsNullData() {
        when(service.findLatest(anyString(), anyInt())).thenReturn(null);

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/graphql", Map.of(
                    "query", "{ latestRulePack(country: \"US\", taxYear: 2099) { id } }"
            ));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.get("data").get("latestRulePack").isNull());
        });
    }

    @Test
    void query_rulePackById_returnsData() {
        when(service.findById("id-2"))
                .thenReturn(Optional.of(dto("id-2", "UK", 2025, "UK-2025.1.0", RulePackStatus.DRAFT)));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/graphql", Map.of(
                    "query", "{ rulePack(id: \"id-2\") { id country } }"
            ));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("UK", body.get("data").get("rulePack").get("country").asText());
        });
    }

    @Test
    void query_rulePacks_returnsList() {
        when(service.findRulePacks(anyString(), anyInt(), anyString(), anyInt(), anyInt()))
                .thenReturn(new RulePackService.PageResult<>(List.of(
                        dto("id-1", "US", 2025, "US-2025.11.0", RulePackStatus.PUBLISHED),
                        dto("id-2", "US", 2025, "US-2025.10.0", RulePackStatus.PUBLISHED)
                ), 2L, 0, 10));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/graphql", Map.of(
                    "query", "{ rulePacks(country: \"US\", taxYear: 2025) { id version } }"
            ));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            JsonNode list = body.get("data").get("rulePacks");
            assertEquals(2, list.size());
        });
    }

    @Test
    void mutation_publishRulePack_returnsUpdated() {
        when(service.publishRulePack("id-3"))
                .thenReturn(dto("id-3", "US", 2025, "US-2025.11.0", RulePackStatus.PUBLISHED));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/graphql", Map.of(
                    "query", "mutation { publishRulePack(id: \"id-3\") { id status } }"
            ));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("PUBLISHED",
                    body.get("data").get("publishRulePack").get("status").asText());
        });
    }

    @Test
    void mutation_deprecateRulePack_returnsUpdated() {
        when(service.deprecateRulePack("id-4"))
                .thenReturn(dto("id-4", "US", 2025, "US-2025.11.0", RulePackStatus.DEPRECATED));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/graphql", Map.of(
                    "query", "mutation { deprecateRulePack(id: \"id-4\") { id status } }"
            ));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("DEPRECATED",
                    body.get("data").get("deprecateRulePack").get("status").asText());
        });
    }

    @Test
    void request_withInvalidJson_returns400() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/graphql", "not-valid-json");
            assertEquals(400, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertNotNull(body.get("error"));
        });
    }

    @Test
    void request_withoutQueryField_returns400() {
        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/graphql", Map.of("variables", Map.of()));
            assertEquals(400, response.code());
        });
    }
}
