package app.salary.api.version;

import java.io.Serial;
import java.util.Map;

/**
 * Raised when a client is older than the configured minimum for its platform. Rendered as a
 * 426 with a JSON body by the handler registered in {@code Main.createApp}.
 *
 * <p>Carries its own body rather than throwing Javalin's {@code HttpResponseException}: that
 * one renders plain text unless the caller sent {@code Accept: application/json}, and even
 * then nests the payload under {@code details}. The upgrade response is a contract the client
 * switches on, so it cannot depend on a request header the client may not set.
 *
 * <p>Modelled on {@code ValidationException} — same "throw from anywhere, render once in
 * Main" shape.
 */
public class UpgradeRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> body;

    public UpgradeRequiredException(Map<String, String> body) {
        super("Upgrade required");
        this.body = Map.copyOf(body);
    }

    public Map<String, String> getBody() {
        return body;
    }
}
