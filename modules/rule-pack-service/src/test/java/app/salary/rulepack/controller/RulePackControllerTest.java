package app.salary.rulepack.controller;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.service.RulePackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulePackControllerTest {

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
            config.routes.exception(IllegalArgumentException.class, (e, ctx) ->
                    ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", String.valueOf(e.getMessage()))));
            new RulePackController(service).register(config.routes);
        });
    }

    private RulePackDto dto(String id, String country, int year, String version, RulePackStatus status) {
        return RulePackDto.builder()
                .id(id)
                .country(country)
                .taxYear(year)
                .version(version)
                .status(status)
                .effectiveDate(LocalDate.of(year, Month.JANUARY, 1))
                .createdAt(LocalDateTime.of(year, Month.JANUARY, 1, 0, 0))
                .checksum("abc123")
                .storagePath("rulepack/" + country + "/" + year + "/" + version + ".json")
                .build();
    }

    @Test
    void list_returnsPagedResults() {
        RulePackDto pack = dto("id-1", "US", 2025, "US-2025.11.0", RulePackStatus.PUBLISHED);
        when(service.findRulePacks("US", 2025, "PUBLISHED", 0, 10))
                .thenReturn(new RulePackService.PageResult<>(List.of(pack), 1L, 0, 10));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/rule-packs?country=US&taxYear=2025");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals(1, body.get("total").asInt());
            assertEquals(0, body.get("page").asInt());
            assertEquals(10, body.get("size").asInt());
            assertEquals("id-1", body.get("rulePacks").get(0).get("id").asText());
        });
    }

    @Test
    void getLatest_returnsDto() {
        RulePackDto pack = dto("id-2", "US", 2025, "US-2025.11.0", RulePackStatus.PUBLISHED);
        when(service.findLatest("US", 2025)).thenReturn(pack);

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/rule-packs/latest?country=US&taxYear=2025");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("US-2025.11.0", body.get("version").asText());
            assertEquals("PUBLISHED",    body.get("status").asText());
        });
    }

    @Test
    void getLatest_whenNotFound_returns404() {
        when(service.findLatest(anyString(), anyInt())).thenReturn(null);

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/rule-packs/latest?country=US&taxYear=2099");
            assertEquals(404, response.code());
        });
    }

    @Test
    void getById_returnsDto() {
        when(service.findById("id-3"))
                .thenReturn(Optional.of(dto("id-3", "UK", 2025, "UK-2025.1.0", RulePackStatus.DRAFT)));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/rule-packs/id-3");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("UK", body.get("country").asText());
        });
    }

    @Test
    void getById_whenMissing_returns404() {
        when(service.findById("nope")).thenReturn(Optional.empty());

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/rule-packs/nope");
            assertEquals(404, response.code());
        });
    }

    @Test
    void download_streamsJson() {
        when(service.downloadRulePack("id-4"))
                .thenReturn(Optional.of(Map.of("version", "US-2025.11.0", "states", List.of("CA", "NY"))));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.get("/v1/rule-packs/id-4/download");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("US-2025.11.0", body.get("version").asText());
            assertTrue(body.get("states").isArray());
        });
    }

    @Test
    void create_returns201WithCreatedDto() {
        RulePackDto created = dto("new-id", "US", 2026, "US-2026.1.0", RulePackStatus.DRAFT);
        when(service.createRulePack(eq("US"), eq(2026), eq("US-2026.1.0"),
                eq(LocalDate.of(2026, Month.JANUARY, 1)), any()))
                .thenReturn(created);

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post(
                    "/v1/rule-packs?country=US&taxYear=2026&version=US-2026.1.0&effectiveDate=2026-01-01",
                    Map.of("dummy", "payload"));
            assertEquals(201, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("new-id",       body.get("id").asText());
            assertEquals("US-2026.1.0",  body.get("version").asText());
            assertEquals("DRAFT",        body.get("status").asText());
        });
    }

    @Test
    void create_whenDuplicate_returns400() {
        when(service.createRulePack(anyString(), anyInt(), anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException("Rule pack already exists: US-2026-US-2026.1.0"));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post(
                    "/v1/rule-packs?country=US&taxYear=2026&version=US-2026.1.0&effectiveDate=2026-01-01",
                    Map.of("any", "body"));
            assertEquals(400, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.get("error").asText().contains("already exists"));
        });
    }

    @Test
    void publish_returnsUpdatedDto() {
        RulePackDto published = dto("id-5", "US", 2025, "US-2025.11.0", RulePackStatus.PUBLISHED);
        when(service.publishRulePack("id-5")).thenReturn(published);

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/rule-packs/id-5/publish");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("PUBLISHED", body.get("status").asText());
            verify(service, times(1)).publishRulePack("id-5");
        });
    }

    @Test
    void publish_whenMissing_returns404() {
        when(service.publishRulePack("nope"))
                .thenThrow(new RuntimeException("Rule pack not found: nope"));

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/rule-packs/nope/publish");
            assertEquals(404, response.code());
        });
    }

    @Test
    void deprecate_returnsUpdatedDto() {
        RulePackDto deprecated = dto("id-6", "US", 2025, "US-2025.11.0", RulePackStatus.DEPRECATED);
        when(service.deprecateRulePack("id-6")).thenReturn(deprecated);

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/rule-packs/id-6/deprecate");
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertEquals("DEPRECATED", body.get("status").asText());
        });
    }
}
