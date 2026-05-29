package app.salary.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Thin wrapper around the Jakarta Validation runtime.
 * <p>
 * Spring's {@code @Valid} on a controller parameter is replaced by an explicit
 * {@code validator.validate(request)} call in each handler. Field-level errors are
 * mapped to a {@code Map<String, String>} response matching the Spring behavior so
 * the iOS app doesn't observe a wire change.
 */
public class RequestValidator {

    private final Validator validator;

    public RequestValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    public <T> void validate(T target) {
        if (target == null) {
            throw new ValidationException(Map.of("body", "Request body must not be null"));
        }
        Set<ConstraintViolation<T>> violations = validator.validate(target);
        if (!violations.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();
            for (ConstraintViolation<T> v : violations) {
                String path = v.getPropertyPath().toString();
                String key = path.isEmpty() ? target.getClass().getSimpleName() : path;
                errors.put(key, v.getMessage());
            }
            throw new ValidationException(errors);
        }
    }
}
