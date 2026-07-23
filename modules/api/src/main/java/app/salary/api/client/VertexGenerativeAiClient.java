package app.salary.api.client;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vertex AI-backed {@link GenerativeAiClient} via the {@code google-genai}
 * SDK. Auth is Application Default Credentials — same story as
 * {@link app.salary.api.store.FirestoreBudgetStore} and friends, needs live
 * GCP and is excluded from the local coverage gate (see root build.gradle's
 * jacocoExcludes and this module's sonar.coverage.exclusions).
 *
 * Never logs the prompt or response text — both may contain salary amounts.
 */
public class VertexGenerativeAiClient implements GenerativeAiClient {

    private static final Logger log = LoggerFactory.getLogger(VertexGenerativeAiClient.class);

    private final Client client;

    public VertexGenerativeAiClient(String projectId, String location) {
        this.client = Client.builder()
                .project(projectId)
                .location(location)
                .vertexAI(true)
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
            log.warn("Gemini request failed: {}", e.getMessage());
            throw new GenerativeAiException("Gemini request failed: " + e.getMessage(), e);
        }
    }
}
