package app.salary.rules;

/**
 * Thrown when an embedded classpath rule pack is missing or cannot be parsed.
 * Unchecked because it is raised inside the Caffeine cache-loader lambda in
 * {@link RulesRegistry}, which cannot propagate checked exceptions.
 */
public class RulePackLoadException extends RuntimeException {

    public RulePackLoadException(String message) {
        super(message);
    }

    public RulePackLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
