package app.salary.api.client;

import com.google.genai.types.Schema;

/**
 * Structured-JSON text generation. {@link VertexGenerativeAiClient} in prod;
 * tests use fakes. Implementations throw {@link GenerativeAiException} when
 * the upstream is unreachable or returns something unusable — callers map
 * that to 503 so the client falls back to its own on-device plan.
 */
public interface GenerativeAiClient {

    /**
     * Sends {@code prompt} to {@code model} and returns raw JSON text
     * conforming to {@code responseSchema}.
     */
    String generateJson(String model, String prompt, Schema responseSchema);
}
