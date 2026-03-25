package app.salary.rulepack.graphql;

import app.salary.rulepack.dto.RulePackDto;
import app.salary.rulepack.entity.RulePackStatus;
import app.salary.rulepack.service.RulePackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GraphQL controller exposing rule pack queries and mutations.
 * Runs alongside the existing REST controller — does not replace it.
 */
@Controller
public class RulePackGraphQLController {

    private static final Logger logger = LoggerFactory.getLogger(RulePackGraphQLController.class);

    private final RulePackService rulePackService;

    @Autowired
    public RulePackGraphQLController(RulePackService rulePackService) {
        this.rulePackService = rulePackService;
    }

    @QueryMapping
    public RulePackGraphQL latestRulePack(@Argument String country, @Argument int taxYear) {
        logger.debug("GraphQL latestRulePack: {} {}", country, taxYear);
        RulePackDto dto = rulePackService.findLatest(country, taxYear);
        return dto != null ? toGraphQL(dto) : null;
    }

    @QueryMapping
    public List<RulePackGraphQL> rulePacks(
            @Argument String country,
            @Argument Integer taxYear,
            @Argument String status,
            @Argument Integer page,
            @Argument Integer size
    ) {
        String resolvedStatus = status != null ? status : RulePackStatus.PUBLISHED.name();
        int resolvedPage = page != null ? page : 0;
        int resolvedSize = size != null ? size : 10;

        Page<RulePackDto> result = rulePackService.findRulePacks(
                country, taxYear, resolvedStatus, PageRequest.of(resolvedPage, resolvedSize));

        return result.getContent().stream().map(this::toGraphQL).collect(Collectors.toList());
    }

    @QueryMapping
    public RulePackGraphQL rulePack(@Argument String id) {
        logger.debug("GraphQL rulePack: {}", id);
        return rulePackService.findById(id).map(this::toGraphQL).orElse(null);
    }

    @MutationMapping
    public RulePackGraphQL createRulePack(
            @Argument String country,
            @Argument int taxYear,
            @Argument String version,
            @Argument String effectiveDate
    ) {
        logger.info("GraphQL createRulePack: {}-{}-{}", country, taxYear, version);
        RulePackDto dto = rulePackService.createRulePack(
                country, taxYear, version,
                LocalDate.parse(effectiveDate),
                Collections.emptyMap()
        );
        return toGraphQL(dto);
    }

    @MutationMapping
    public RulePackGraphQL publishRulePack(@Argument String id) {
        logger.info("GraphQL publishRulePack: {}", id);
        return toGraphQL(rulePackService.publishRulePack(id));
    }

    @MutationMapping
    public RulePackGraphQL deprecateRulePack(@Argument String id) {
        logger.info("GraphQL deprecateRulePack: {}", id);
        return toGraphQL(rulePackService.deprecateRulePack(id));
    }

    private RulePackGraphQL toGraphQL(RulePackDto dto) {
        return new RulePackGraphQL(
                dto.getId(),
                dto.getCountry(),
                dto.getTaxYear(),
                dto.getVersion(),
                dto.getStatus() != null ? dto.getStatus().name() : null,
                dto.getEffectiveDate() != null ? dto.getEffectiveDate().toString() : null,
                dto.getStoragePath(),
                dto.getChecksum(),
                dto.getCreatedAt() != null ? dto.getCreatedAt().toString() : null
        );
    }
}
