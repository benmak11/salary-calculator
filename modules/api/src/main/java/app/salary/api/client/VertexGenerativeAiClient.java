package app.salary.api.client;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Vertex AI-backed {@link GenerativeAiClient} via the {@code google-genai}
 * SDK. Auth is Application Default Credentials — same story as
 * {@link app.salary.api.store.FirestoreBudgetStore} and friends, needs live
 * GCP and is excluded from the local coverage gate (see root build.gradle's
 * jacocoExcludes and this module's sonar.coverage.exclusions).
 * <p>
 * Never logs the prompt or response text — both may contain salary amounts.
 *
 * <p><b>Carries an explicit deadline.</b> The SDK applies no timeout of its own, so before
 * this a hung generate call rode until Cloud Run's request timeout (300s by default),
 * holding the caller and billing the whole time. {@code FinnhubStockClient} and
 * {@code HttpRulePackClient} have both always set 5s; this one set nothing. Gemini is slower
 * than either, so the default here is longer rather than matched to them.
 */
public class VertexGenerativeAiClient implements GenerativeAiClient {

    private static final Logger log = LoggerFactory.getLogger(VertexGenerativeAiClient.class);

    private final Client client;
    private final Duration timeout;

    public VertexGenerativeAiClient(String projectId, String location, Duration timeout) {
        this.timeout = timeout;
        this.client = Client.builder()
                .project(projectId)
                .location(location)
                .vertexAI(true)
                // HttpOptions.timeout is in MILLISECONDS — passing a seconds value here
                // would be a 20ms deadline that fails every call.
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(timeout.toMillis()))
                        .build())
                .build();
    }

    @Override
    public String generateJson(String model, String prompt, Schema responseSchema) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();
        try {
            GenerateContentResponse response = client.models.generateContent(model, prompt, config);
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new GenerativeAiException("Empty response from " + model);
            }
            return text;
        } catch (GenerativeAiException e) {
            throw e;
        } catch (RuntimeException e) {
            // Timeouts are reported separately from other failures: a deadline breach is an
            // upstream-capacity signal, while the rest are usually request-shaped.
            if (isTimeout(e)) {
                log.warn("Gemini request timed out after {}s", timeout.toSeconds());
                throw new GenerativeAiException(
                        "Gemini request timed out after " + timeout.toSeconds() + "s", e);
            }
            log.warn("Gemini request failed: {}", e.getMessage());
            throw new GenerativeAiException("Gemini request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Whether a failure is a deadline breach.
     *
     * <p>Walks the cause chain because the SDK wraps the transport exception: the timeout
     * arrives as a {@code SocketTimeoutException} (or a {@code TimeoutException} on the
     * async path) several levels below whatever the SDK throws, so checking only the top
     * exception would classify every timeout as a generic failure.
     *
     * <p>{@code SocketTimeoutException} needs no clause of its own — it extends
     * {@code InterruptedIOException}, which is already matched below.
     *
     * <p>Package-private so it can be tested without constructing the enclosing class, which
     * needs live GCP credentials and is therefore coverage-excluded.
     */
    static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException
                    || cause instanceof InterruptedIOException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;  // self-referencing cause: stop rather than loop forever
            }
        }
        return false;
    }
}
