package app.salary.rulepack.graphql;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.service.RulePackService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * GraphQL endpoint for the rule pack service, mounted at {@code POST /graphql}.
 *
 * Spring GraphQL's annotation-driven controller is replaced with raw graphql-java
 * — we load the SDL from {@code resources/graphql/schema.graphqls} and bind data
 * fetchers manually. The wire schema and resolver behavior are unchanged.
 */
public class RulePackGraphQLController {

    private static final Logger logger = LoggerFactory.getLogger(RulePackGraphQLController.class);
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};

    private final RulePackService rulePackService;
    private final ObjectMapper objectMapper;
    private final GraphQL graphQL;

    public RulePackGraphQLController(RulePackService rulePackService, ObjectMapper objectMapper) {
        this.rulePackService = rulePackService;
        this.objectMapper = objectMapper;
        this.graphQL = buildGraphQL();
    }

    public void register(RoutesConfig routes) {
        routes.post("/graphql", this::handle);
    }

    @SuppressWarnings("unchecked")
    private void handle(Context ctx) {
        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(JSON_MAP.getType());
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Invalid JSON body"));
            return;
        }
        if (body == null || !body.containsKey("query")) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "GraphQL query is required"));
            return;
        }

        ExecutionInput.Builder input = ExecutionInput.newExecutionInput()
                .query((String) body.get("query"));
        if (body.get("operationName") instanceof String op) {
            input.operationName(op);
        }
        if (body.get("variables") instanceof Map<?, ?> vars) {
            input.variables((Map<String, Object>) vars);
        }

        ExecutionResult result = graphQL.execute(input.build());
        ctx.json(result.toSpecification());
    }

    private GraphQL buildGraphQL() {
        String sdl = loadSdl("/graphql/schema.graphqls");
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("latestRulePack", latestRulePack())
                        .dataFetcher("rulePacks", rulePacks())
                        .dataFetcher("rulePack", rulePackById()))
                .type("Mutation", builder -> builder
                        .dataFetcher("createRulePack", createRulePack())
                        .dataFetcher("publishRulePack", publishRulePack())
                        .dataFetcher("deprecateRulePack", deprecateRulePack()))
                .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private DataFetcher<Map<String, Object>> latestRulePack() {
        return env -> {
            String country = env.getArgument("country");
            Integer taxYear = env.getArgument("taxYear");
            logger.debug("GraphQL latestRulePack: {} {}", country, taxYear);
            RulePackDto dto = rulePackService.findLatest(country, taxYear);
            return dto != null ? toGraphQL(dto) : null;
        };
    }

    private DataFetcher<List<Map<String, Object>>> rulePacks() {
        return env -> {
            String country = env.getArgument("country");
            Integer taxYear = env.getArgument("taxYear");
            String status = env.getArgument("status");
            Integer page = env.getArgument("page");
            Integer size = env.getArgument("size");

            String resolvedStatus = status != null ? status : RulePackStatus.PUBLISHED.name();
            int resolvedPage = page != null ? page : 0;
            int resolvedSize = size != null ? size : 10;

            RulePackService.PageResult<RulePackDto> result =
                    rulePackService.findRulePacks(country, taxYear, resolvedStatus,
                            resolvedPage, resolvedSize);
            return result.content().stream().map(this::toGraphQL).collect(Collectors.toList());
        };
    }

    private DataFetcher<Map<String, Object>> rulePackById() {
        return env -> {
            String id = env.getArgument("id");
            return rulePackService.findById(id).map(this::toGraphQL).orElse(null);
        };
    }

    private DataFetcher<Map<String, Object>> createRulePack() {
        return env -> {
            String country = env.getArgument("country");
            Integer taxYear = env.getArgument("taxYear");
            String version = env.getArgument("version");
            String effectiveDate = env.getArgument("effectiveDate");

            logger.info("GraphQL createRulePack: {}-{}-{}", country, taxYear, version);
            assert effectiveDate != null;
            RulePackDto dto = rulePackService.createRulePack(
                    country, taxYear, version,
                    LocalDate.parse(effectiveDate),
                    Collections.emptyMap());
            return toGraphQL(dto);
        };
    }

    private DataFetcher<Map<String, Object>> publishRulePack() {
        return env -> {
            String id = env.getArgument("id");
            logger.info("GraphQL publishRulePack: {}", id);
            return toGraphQL(rulePackService.publishRulePack(id));
        };
    }

    private DataFetcher<Map<String, Object>> deprecateRulePack() {
        return env -> {
            String id = env.getArgument("id");
            logger.info("GraphQL deprecateRulePack: {}", id);
            return toGraphQL(rulePackService.deprecateRulePack(id));
        };
    }

    private Map<String, Object> toGraphQL(RulePackDto dto) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", dto.getId());
        map.put("country", dto.getCountry());
        map.put("taxYear", dto.getTaxYear());
        map.put("version", dto.getVersion());
        map.put("status", dto.getStatus() != null ? dto.getStatus().name() : null);
        map.put("effectiveDate", dto.getEffectiveDate() != null ? dto.getEffectiveDate().toString() : null);
        map.put("storagePath", dto.getStoragePath());
        map.put("checksum", dto.getChecksum());
        map.put("createdAt", dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null);
        return map;
    }

    private String loadSdl(String classpathLocation) {
        try (InputStream is = getClass().getResourceAsStream(classpathLocation)) {
            if (is == null) {
                throw new RuntimeException("GraphQL schema not found on classpath: " + classpathLocation);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                 Stream<String> lines = reader.lines()) {
                return lines.collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load GraphQL schema: " + classpathLocation, e);
        }
    }
}
