package app.salary.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HttpRulePackClientTest {

    private static final String BASE_URL = "https://rule-pack.example";

    @Test
    void attachesBearerTokenWhenSupplierProvided() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> resp = mock(HttpResponse.class);
        // status 200 + empty-id body short-circuits the second call; we only need
        // to capture the first request and inspect its headers.
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{}");
        doReturn(resp).when(http).send(any(HttpRequest.class), any());

        Supplier<String> tokenSupplier = () -> "test-token";
        HttpRulePackClient client = new HttpRulePackClient(
                http, new ObjectMapper(), BASE_URL, tokenSupplier);

        client.fetchLatest("US", 2025);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any());
        HttpRequest sent = captor.getValue();
        assertEquals("Bearer test-token",
                sent.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void cachesSuccessfulFetchAndSkipsNetworkOnRepeatCall() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> metadataResp = mock(HttpResponse.class);
        when(metadataResp.statusCode()).thenReturn(200);
        when(metadataResp.body()).thenReturn("{\"id\":\"pack-1\"}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> downloadResp = mock(HttpResponse.class);
        when(downloadResp.statusCode()).thenReturn(200);
        // Minimal valid pack: an empty JSON object would (correctly) be treated as a
        // failed download, so give the pack one real field.
        when(downloadResp.body()).thenReturn("{\"metadata\":{\"version\":\"t\"}}");

        // First call: returns metadata. Second call: returns download. Subsequent calls
        // would also return the download stub, but we assert they never happen.
        doReturn(metadataResp, downloadResp)
                .when(http).send(any(HttpRequest.class), any());

        HttpRulePackClient client = new HttpRulePackClient(
                http, new ObjectMapper(), BASE_URL);

        var first  = client.fetchLatest("US", 2025);
        var second = client.fetchLatest("US", 2025);

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        // Exactly two HTTP calls total (metadata + download) across both invocations —
        // the second fetchLatest is served from cache.
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    void doesNotCacheFailedFetch() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> errorResp = mock(HttpResponse.class);
        when(errorResp.statusCode()).thenReturn(500);
        when(errorResp.body()).thenReturn("");
        doReturn(errorResp).when(http).send(any(HttpRequest.class), any());

        HttpRulePackClient client = new HttpRulePackClient(
                http, new ObjectMapper(), BASE_URL);

        var first  = client.fetchLatest("US", 2025);
        var second = client.fetchLatest("US", 2025);

        assertFalse(first.isPresent());
        assertFalse(second.isPresent());
        // Both calls must re-attempt the network so a transient upstream failure
        // doesn't pin the client to the classpath fallback for the cache TTL.
        verify(http, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    void returnsEmptyAndDoesNotCacheWhenDownloadFails() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> metadataResp = mock(HttpResponse.class);
        when(metadataResp.statusCode()).thenReturn(200);
        when(metadataResp.body()).thenReturn("{\"id\":\"pack-1\"}");

        @SuppressWarnings("unchecked")
        HttpResponse<String> downloadResp = mock(HttpResponse.class);
        when(downloadResp.statusCode()).thenReturn(500);
        when(downloadResp.body()).thenReturn("");

        // Both attempts: metadata succeeds, download fails.
        doReturn(metadataResp, downloadResp, metadataResp, downloadResp)
                .when(http).send(any(HttpRequest.class), any());

        HttpRulePackClient client = new HttpRulePackClient(
                http, new ObjectMapper(), BASE_URL);

        var first  = client.fetchLatest("US", 2025);
        var second = client.fetchLatest("US", 2025);

        // A failed download must NOT surface as an (all-null) pack — the orchestrator
        // needs the empty Optional to fall back to the embedded classpath rules.
        assertFalse(first.isPresent());
        assertFalse(second.isPresent());
        // And it must not be cached: both invocations hit metadata + download.
        verify(http, times(4)).send(any(HttpRequest.class), any());
    }

    @Test
    void returnsEmptyWithoutNetworkCallWhenBaseUrlBlank() throws Exception {
        HttpClient http = mock(HttpClient.class);

        assertFalse(new HttpRulePackClient(http, new ObjectMapper(), "")
                .fetchLatest("US", 2025).isPresent());
        assertFalse(new HttpRulePackClient(http, new ObjectMapper(), null)
                .fetchLatest("US", 2025).isPresent());

        verify(http, times(0)).send(any(HttpRequest.class), any());
    }

    @Test
    void returnsEmptyWhenRequestThrows() throws Exception {
        HttpClient http = mock(HttpClient.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("connection reset"))
                .when(http).send(any(HttpRequest.class), any());

        HttpRulePackClient client = new HttpRulePackClient(
                http, new ObjectMapper(), BASE_URL);

        assertFalse(client.fetchLatest("US", 2025).isPresent());
    }

    @Test
    void omitsAuthorizationHeaderWhenNoSupplier() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{}");
        doReturn(resp).when(http).send(any(HttpRequest.class), any());

        HttpRulePackClient client = new HttpRulePackClient(
                http, new ObjectMapper(), BASE_URL);

        client.fetchLatest("US", 2025);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any());
        HttpRequest sent = captor.getValue();
        assertFalse(sent.headers().firstValue("Authorization").isPresent(),
                "Authorization header must not be set when no token supplier is configured");
    }
}
