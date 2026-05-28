package app.salary.api.service;

import app.salary.common.dto.CalculateResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory store that keeps the most recent {@value #MAX_SIZE} calculation results
 * so that downstream endpoints (insights, impact-analysis) can retrieve them by
 * calculationId without requiring a persistent database.
 *
 * TODO: Replace with Redis or a proper persistence layer when user authentication
 *       and multi-device support are added.
 */
@Service
public class CalculationStore {

    private static final int MAX_SIZE = 500;

    private final Map<String, CalculateResponse> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CalculateResponse> eldest) {
            return size() > MAX_SIZE;
        }
    };

    public void store(CalculateResponse response) {
        if (response != null && response.getCalculationId() != null) {
            cache.put(response.getCalculationId(), response);
        }
    }

    public Optional<CalculateResponse> find(String calculationId) {
        return Optional.ofNullable(cache.get(calculationId));
    }
}
