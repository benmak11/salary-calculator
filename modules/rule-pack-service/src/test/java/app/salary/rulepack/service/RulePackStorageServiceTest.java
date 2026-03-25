package app.salary.rulepack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.*;
import app.salary.rulepack.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RulePackStorageServiceTest {

    @Mock
    private Storage storage;

    @Mock
    private Blob blob;

    private RulePackStorageService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new RulePackStorageService(storage, "test-bucket", objectMapper);
    }

    @Test
    void upload_shouldUploadToGCS() throws Exception {
        Map<String, Object> rulePackJson = Map.of("rule", "value");

        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(blob);

        String result = service.upload("US", 2025, "2025.1.0", rulePackJson);

        assertNotNull(result);
        assertTrue(result.contains("US"));
        assertTrue(result.contains("2025"));
        assertTrue(result.contains("2025.1.0"));

        verify(storage).create(any(BlobInfo.class), any(byte[].class));
    }

    @Test
    void upload_shouldBuildCorrectBlobName() {
        Map<String, Object> rulePackJson = Map.of("rule", "value");

        when(storage.create(any(BlobInfo.class), any(byte[].class))).thenReturn(blob);

        String result = service.upload("UK", 2025, "2025.2.0", rulePackJson);

        assertEquals("rulepack/UK/2025/2025.2.0/rulepack-UK-2025-2025.2.0.json", result);
    }

    @Test
    void download_shouldReturnRulePackContent() throws Exception {
        String jsonContent = "{\"rule\":\"value\"}";
        byte[] contentBytes = jsonContent.getBytes(StandardCharsets.UTF_8);

        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.getContent()).thenReturn(contentBytes);

        Optional<Map<String, Object>> result = service.download("test-blob");

        assertTrue(result.isPresent());
        assertEquals("value", result.get().get("rule"));
    }

    @Test
    void download_whenBlobNotFound_shouldReturnEmpty() {
        when(storage.get(any(BlobId.class))).thenReturn(null);

        Optional<Map<String, Object>> result = service.download("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    void download_whenInvalidJson_shouldThrowStorageException() {
        byte[] invalidJson = "invalid json".getBytes(StandardCharsets.UTF_8);

        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.getContent()).thenReturn(invalidJson);

        assertThrows(StorageException.class, () -> service.download("test-blob"));
    }

    @Test
    void delete_shouldDeleteFromGCS() {
        when(storage.delete(any(BlobId.class))).thenReturn(true);

        boolean result = service.delete("test-blob");

        assertTrue(result);
        verify(storage).delete(any(BlobId.class));
    }

    @Test
    void delete_whenBlobNotFound_shouldReturnFalse() {
        when(storage.delete(any(BlobId.class))).thenReturn(false);

        boolean result = service.delete("nonexistent");

        assertFalse(result);
    }

    @Test
    void exists_whenBlobExists_shouldReturnTrue() {
        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(blob.exists()).thenReturn(true);

        boolean result = service.exists("test-blob");

        assertTrue(result);
    }

    @Test
    void exists_whenBlobNotFound_shouldReturnFalse() {
        when(storage.get(any(BlobId.class))).thenReturn(null);

        boolean result = service.exists("nonexistent");

        assertFalse(result);
    }

    @Test
    void generateSignedUrl_shouldReturnUrl() throws Exception {
        URL mockUrl = new URL("https://storage.googleapis.com/signed-url");

        when(storage.signUrl(any(BlobInfo.class), anyLong(), any())).thenReturn(mockUrl);

        String result = service.generateSignedUrl("test-blob");

        assertNotNull(result);
        assertTrue(result.contains("storage.googleapis.com"));
    }

    @Test
    void listByCountryAndYear_shouldReturnBlobNames() {
        Blob blob1 = mock(Blob.class);
        Blob blob2 = mock(Blob.class);
        when(blob1.getName()).thenReturn("rulepack/US/2025/v1/file1.json");
        when(blob2.getName()).thenReturn("rulepack/US/2025/v2/file2.json");

        Page<Blob> blobPage = mock(Page.class);
        when(blobPage.iterateAll()).thenReturn(Arrays.asList(blob1, blob2));
        when(storage.list(eq("test-bucket"), any(Storage.BlobListOption.class))).thenReturn(blobPage);

        List<String> result = service.listByCountryAndYear("US", 2025);

        assertEquals(2, result.size());
        assertTrue(result.get(0).contains("US"));
        assertTrue(result.get(0).contains("2025"));
    }
}
