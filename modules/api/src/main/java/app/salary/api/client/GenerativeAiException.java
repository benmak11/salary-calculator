package app.salary.api.client;

/** Upstream generative-AI provider failed, was unreachable, or returned unusable output. Mapped to 503 at the edge. */
public class GenerativeAiException extends RuntimeException {
    public GenerativeAiException(String message) {
        super(message);
    }

    public GenerativeAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
