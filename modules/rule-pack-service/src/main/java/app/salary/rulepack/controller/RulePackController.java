package app.salary.rulepack.controller;

import app.salary.rulepack.constants.ApiConstants;
import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.dto.RulePackListResponse;
import app.salary.rulepack.service.RulePackService;
import com.fasterxml.jackson.core.type.TypeReference;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST endpoints for rule pack management. CORS is open for now to match the
 * previous {@code @CrossOrigin(origins = "*")} behavior; tighten before production.
 */
public class RulePackController {

    private static final Logger logger = LoggerFactory.getLogger(RulePackController.class);
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final RulePackService rulePackService;

    public RulePackController(RulePackService rulePackService) {
        this.rulePackService = rulePackService;
    }

    public void register(RoutesConfig routes) {
        routes.get(    "/v1/rule-packs",                  this::list);
        routes.get(    "/v1/rule-packs/latest",           this::getLatest);
        routes.get(    "/v1/rule-packs/{id}",             this::getById);
        routes.get(    "/v1/rule-packs/{id}/download",    this::download);
        routes.post(   "/v1/rule-packs",                  this::create);
        routes.post(   "/v1/rule-packs/{id}/publish",     this::publish);
        routes.post(   "/v1/rule-packs/{id}/deprecate",   this::deprecate);
    }

    private void list(Context ctx) {
        String country = ctx.queryParam(ApiConstants.COUNTRY);
        Integer taxYear = ctx.queryParamAsClass(ApiConstants.TAX_YEAR, Integer.class).getOrNull();
        String status = ctx.queryParamAsClass("status", String.class).getOrDefault("PUBLISHED");
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(10);

        logger.info("Listing rule packs — country: {}, taxYear: {}, status: {}", country, taxYear, status);

        RulePackService.PageResult<RulePackDto> result =
                rulePackService.findRulePacks(country, taxYear, status, page, size);

        ctx.json(new RulePackListResponse(result.content(), result.total(), page, size));
    }

    private void getLatest(Context ctx) {
        String country = ctx.queryParamAsClass(ApiConstants.COUNTRY, String.class).get();
        Integer taxYear = ctx.queryParamAsClass(ApiConstants.TAX_YEAR, Integer.class).get();

        RulePackDto dto = rulePackService.findLatest(country, taxYear);
        if (dto == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        ctx.json(dto);
    }

    private void getById(Context ctx) {
        rulePackService.findById(ctx.pathParam("id"))
                .ifPresentOrElse(ctx::json, () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    private void download(Context ctx) {
        rulePackService.downloadRulePack(ctx.pathParam("id"))
                .ifPresentOrElse(
                        json -> ctx.contentType("application/json").json(json),
                        () -> ctx.status(HttpStatus.NOT_FOUND)
                );
    }

    private void create(Context ctx) {
        try {
            String country = ctx.queryParamAsClass(ApiConstants.COUNTRY, String.class).get();
            Integer taxYear = ctx.queryParamAsClass(ApiConstants.TAX_YEAR, Integer.class).get();
            String version = ctx.queryParamAsClass("version", String.class).get();
            String effectiveDate = ctx.queryParamAsClass("effectiveDate", String.class).get();
            Map<String, Object> rulePackJson = ctx.bodyAsClass(OBJECT_MAP.getType());

            RulePackDto created = rulePackService.createRulePack(
                    country, taxYear, version,
                    LocalDate.parse(effectiveDate),
                    rulePackJson != null ? rulePackJson : Map.of()
            );
            ctx.status(HttpStatus.CREATED).json(created);
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(ApiConstants.ERROR, String.valueOf(e.getMessage())));
        } catch (Exception e) {
            logger.error("Error creating rule pack", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of(ApiConstants.ERROR, "Internal server error"));
        }
    }

    private void publish(Context ctx) {
        try {
            ctx.json(rulePackService.publishRulePack(ctx.pathParam("id")));
        } catch (RuntimeException e) {
            ctx.status(HttpStatus.NOT_FOUND);
        }
    }

    private void deprecate(Context ctx) {
        try {
            ctx.json(rulePackService.deprecateRulePack(ctx.pathParam("id")));
        } catch (RuntimeException e) {
            ctx.status(HttpStatus.NOT_FOUND);
        }
    }
}
