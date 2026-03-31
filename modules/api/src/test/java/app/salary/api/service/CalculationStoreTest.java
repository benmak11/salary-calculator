package app.salary.api.service;

import app.salary.common.dto.CalculateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CalculationStoreTest {

    private CalculationStore store;

    @BeforeEach
    void setUp() {
        store = new CalculationStore();
    }

    @Test
    void store_andFind_shouldReturnStoredResponse() {
        CalculateResponse response = new CalculateResponse();
        response.setCalculationId("c_abc123");
        response.setGrossPerCadence(100000.0);

        store.store(response);
        Optional<CalculateResponse> found = store.find("c_abc123");

        assertTrue(found.isPresent());
        assertEquals(100000.0, found.get().getGrossPerCadence());
    }

    @Test
    void find_withUnknownId_shouldReturnEmpty() {
        Optional<CalculateResponse> found = store.find("c_unknown");

        assertFalse(found.isPresent());
    }

    @Test
    void store_withNullResponse_shouldNotThrow() {
        assertDoesNotThrow(() -> store.store(null));
    }

    @Test
    void store_withNullId_shouldNotThrow() {
        CalculateResponse response = new CalculateResponse();
        response.setCalculationId(null);

        assertDoesNotThrow(() -> store.store(response));
    }

    @Test
    void store_multipleResponses_shouldAllBeRetrievable() {
        for (int i = 0; i < 10; i++) {
            CalculateResponse r = new CalculateResponse();
            r.setCalculationId("c_" + i);
            r.setGrossPerCadence((double) i * 1000);
            store.store(r);
        }

        for (int i = 0; i < 10; i++) {
            assertTrue(store.find("c_" + i).isPresent());
        }
    }

    @Test
    void store_duplicateId_shouldOverwritePreviousEntry() {
        CalculateResponse first = new CalculateResponse();
        first.setCalculationId("c_dup");
        first.setGrossPerCadence(50000.0);
        store.store(first);

        CalculateResponse second = new CalculateResponse();
        second.setCalculationId("c_dup");
        second.setGrossPerCadence(75000.0);
        store.store(second);

        Optional<CalculateResponse> found = store.find("c_dup");
        assertTrue(found.isPresent());
        assertEquals(75000.0, found.get().getGrossPerCadence());
    }
}
