package app.salary.rulepack.graphql;

/**
 * GraphQL response type for rule packs.
 * All date fields are represented as ISO-8601 strings to match the schema.graphqls definition.
 */
public record RulePackGraphQL(
        String id,
        String country,
        Integer taxYear,
        String version,
        String status,
        String effectiveDate,
        String storagePath,
        String checksum,
        String createdAt
) {}
