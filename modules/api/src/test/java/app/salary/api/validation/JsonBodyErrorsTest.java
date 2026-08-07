package app.salary.api.validation;

import app.salary.common.dto.CalculateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonBodyErrorsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Provokes a real Jackson failure rather than mocking one, so the path data is genuine. */
    private static MismatchedInputException parseFailure(String json) {
        return assertThrows(MismatchedInputException.class,
                () -> MAPPER.readValue(json, CalculateRequest.class));
    }

    @Test
    void badEnumValue_namesTheFieldAndListsTheAcceptedValues() {
        MismatchedInputException e = parseFailure(
                "{\"earnings\":{\"salary\":{\"amount\":100000,\"basis\":\"ANNUAL\"}}}");

        Map<String, String> body = JsonBodyErrors.forMismatch(e);

        assertEquals(1, body.size());
        String message = body.get("earnings.salary.basis");
        assertTrue(message.contains("PER_YEAR"), message);
        assertTrue(message.contains("PER_PERIOD"), message);
    }

    @Test
    void wrongType_namesTheFieldAndTheTypeItWanted() {
        MismatchedInputException e = parseFailure("{\"taxYear\":{\"nested\":\"object\"}}");

        Map<String, String> body = JsonBodyErrors.forMismatch(e);

        assertEquals("is not a valid Integer", body.get("taxYear"));
    }

    @Test
    void messageNeverEchoesTheSubmittedValue() {
        // The whole point of building our own message: Jackson's quotes the input, and on an
        // amount field that is the caller's salary going into logs and back over the wire.
        MismatchedInputException e = parseFailure(
                "{\"earnings\":{\"salary\":{\"amount\":\"150000abc\",\"basis\":\"PER_YEAR\"}}}");

        Map<String, String> body = JsonBodyErrors.forMismatch(e);

        assertTrue(e.getMessage().contains("150000"), "precondition: Jackson quotes the input");
        assertFalse(body.toString().contains("150000"), "our body must not: " + body);
        assertEquals("is not a valid Double", body.get("earnings.salary.amount"));
    }

    @Test
    void unparseableJson_pointsAtTheBodyRatherThanAField() {
        assertEquals(Map.of("body", "is not valid JSON"), JsonBodyErrors.forUnparseableJson());
    }
}
