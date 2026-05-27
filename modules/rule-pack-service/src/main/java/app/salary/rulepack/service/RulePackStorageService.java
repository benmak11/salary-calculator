package app.salary.rulepack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.*;
import app.salary.rulepack.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.concurrent.TimeUnit.*;

/**
 * Service for managing rule pack storage in Google Cloud Storage
 * Handles upload, download, deletion, and retrieval of rule pack JSON files
 */
public class RulePackStorageService {

    private static final Logger logger = LoggerFactory.getLogger(RulePackStorageService.class);

    private final Storage storage;
    private final String bucketName;
    private final ObjectMapper objectMapper;

    public RulePackStorageService(
            Storage storage,
            String bucketName,
            ObjectMapper objectMapper
    ) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.objectMapper = objectMapper;
        logger.info("Initialized RulePackStorageService with bucket: {}", bucketName);
    }

    /**
     * Upload rule pack JSON to Google Cloud Storage
     *
     * @param country Country code (e.g., "US")
     * @param taxYear Tax year (e.g., 2025)
     * @param version Version string (e.g., "2025.10.0")
     * @param rulePackJson Rule pack JSON as Map
     * @return Storage path in GCS
     * @throws StorageException if upload fails
     */
    public String upload(
            String country,
            Integer taxYear,
            String version,
            Map<String, Object> rulePackJson
    ) {
        String blobName = buildBlobName(country, taxYear, version);

        try {
            String jsonContent = objectMapper.writeValueAsString(rulePackJson);
            byte[] jsonBytes = jsonContent.getBytes(StandardCharsets.UTF_8);

            BlobId blobId = BlobId.of(bucketName, blobName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType("application/json")
                    .setMetadata(Map.of(
                            "country", country,
                            "taxYear", String.valueOf(taxYear),
                            "version", version,
                            "uploadedAt", String.valueOf(System.currentTimeMillis())
                    ))
                    .build();

            storage.create(blobInfo, jsonBytes);
            logger.info("Uploaded rule pack to GCS: {}", blobName);

            return blobName;
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize rule pack", e);
            throw new StorageException("Failed to serialize rule pack", e);
        }
    }

    /**
     * Download rule pack JSON from Google Cloud Storage
     *
     * @param blobName Storage path/blob name
     * @return Rule pack as Map, or empty if not found
     * @throws StorageException if download fails
     */
    public Optional<Map<String, Object>> download(String blobName) {
        try {
            BlobId blobId = BlobId.of(bucketName, blobName);
            Blob blob = storage.get(blobId);

            if (blob == null) {
                logger.warn("Blob not found in GCS: {}", blobName);
                return Optional.empty();
            }

            byte[] content = blob.getContent();
            Map<String, Object> rulePackJson = objectMapper.readValue(
                    content, new TypeReference<Map<String, Object>>() {});

            return Optional.of(rulePackJson);
        } catch (IOException e) {
            logger.error("Failed to download rule pack: {}", blobName, e);
            throw new StorageException("Failed to deserialize rule pack: " + blobName, e);
        }
    }

    /**
     * Delete rule pack from Google Cloud Storage
     *
     * @param blobName Storage path/blob name
     * @return true if deleted, false if not found
     */
    public boolean delete(String blobName) {
        BlobId blobId = BlobId.of(bucketName, blobName);
        boolean deleted = storage.delete(blobId);
        logger.info("Deleted blob from GCS: {} (success: {})", blobName, deleted);
        return deleted;
    }

    /**
     * Check if blob exists in GCS
     *
     * @param blobName Storage path/blob name
     * @return true if exists, false otherwise
     */
    public boolean exists(String blobName) {
        BlobId blobId = BlobId.of(bucketName, blobName);
        Blob blob = storage.get(blobId);
        return blob != null && blob.exists();
    }

    /**
     * Generate signed URL for direct blob access (valid 24 hours)
     *
     * @param blobName Storage path/blob name
     * @return URL string
     */
    public String generateSignedUrl(String blobName) {
        BlobId blobId = BlobId.of(bucketName, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        String url = storage.signUrl(blobInfo, 24, HOURS).toString();
//        String url = storage.signUrl(
//                blobId,
//                24,
//                HOURS,
//                Storage.SignUrlOption.withV4Signature()
//        ).toString();
        logger.debug("Generated signed URL for: {}", blobName);
        return url;
    }

    /**
     * List all blobs for a country and tax year
     *
     * @param country Country code
     * @param taxYear Tax year
     * @return List of blob names
     */
    public List<String> listByCountryAndYear(String country, Integer taxYear) {
        String prefix = String.format("rulepack/%s/%d/", country, taxYear);

        return StreamSupport.stream(
                        storage.list(bucketName, Storage.BlobListOption.prefix(prefix))
                                .iterateAll().spliterator(),
                        false
                )
                .map(Blob::getName)
                .collect(Collectors.toList());
    }

    /**
     * Build GCS blob name following naming convention
     */
    private String buildBlobName(String country, Integer taxYear, String version) {
        return String.format(
                "rulepack/%s/%d/%s/rulepack-%s-%d-%s.json",
                country,
                taxYear,
                version,
                country,
                taxYear,
                version
        );
    }
}
