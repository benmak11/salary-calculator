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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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
        org.mockito.Mockito.doReturn(resp).when(http).send(any(HttpRequest.class), any());

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
    void omitsAuthorizationHeaderWhenNoSupplier() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{}");
        org.mockito.Mockito.doReturn(resp).when(http).send(any(HttpRequest.class), any());

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
