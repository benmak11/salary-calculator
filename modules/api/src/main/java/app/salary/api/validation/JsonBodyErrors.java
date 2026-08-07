package app.salary.api.validation;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a Jackson parse failure into the same {@code {field: message}} body that
 * {@link ValidationException} produces, so a caller sees one error shape whether the request
 * failed to deserialize or failed validation just after.
 *
 * <p>Messages are built from the target type, never from the submitted value. Jackson's own
 * message quotes the offending input — {@code Cannot deserialize value of type `Double` from
 * String "150000"} — and on a salary field that would put earnings straight into a log line
 * and into a response body the iOS client surfaces to the user verbatim.
 */
public final class JsonBodyErrors {

    private JsonBodyErrors() {
    }

    /** Body for input that parsed as JSON but did not fit the target type. */
    public static Map<String, String> forMismatch(MismatchedInputException e) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put(fieldPath(e), describe(e.getTargetType()));
        return body;
    }

    /** Body for input that is not JSON at all, where there is no field to point at. */
    public static Map<String, String> forUnparseableJson() {
        return Map.of("body", "is not valid JSON");
    }

    /**
     * Dotted path to the offending field, e.g. {@code earnings.salary.basis}. Field names come
     * from our own DTOs, so unlike the value they are safe to echo back and to log.
     */
    public static String fieldPath(JsonMappingException e) {
        String path = e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
        return path.isBlank() ? "body" : path;
    }

    private static String describe(Class<?> targetType) {
        if (targetType == null) {
            return "could not be parsed";
        }
        if (targetType.isEnum()) {
            // Enum constants are ours, not the caller's, so listing them is safe and saves a
            // round trip through the docs for whoever sent the wrong one.
            return "must be one of " + Arrays.stream(targetType.getEnumConstants())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        return "is not a valid " + targetType.getSimpleName();
    }
}
