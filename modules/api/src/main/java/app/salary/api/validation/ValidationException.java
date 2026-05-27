package app.salary.api.validation;

import java.util.Map;

/**
 * Raised when a request body fails Jakarta Validation. The Javalin exception mapper in
 * {@code Main} translates this to a 400 with a {@code {field: message}} body, matching
 * the original Spring {@code MethodArgumentNotValidException} shape.
 */
public class ValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super(errors.toString());
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
