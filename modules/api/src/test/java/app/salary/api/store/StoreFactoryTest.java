package app.salary.api.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/**
 * Pins the one rule the factory exists to hold: a null {@link Firestore} means run in
 * memory. Tests rely on that — {@code ENABLE_GCP=false} is what keeps the suite from ever
 * needing a Firestore emulator — so a regression here would be felt everywhere at once.
 */
class StoreFactoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fallsBackToInMemoryWhenFirestoreIsAbsent() {
        assertInstanceOf(InMemoryUserDirectory.class, StoreFactory.userDirectory(null));
        assertInstanceOf(InMemoryAccountDirectory.class, StoreFactory.accountDirectory(null));
        assertInstanceOf(InMemoryCalculationStore.class, StoreFactory.calculationStore(null, MAPPER));
        assertInstanceOf(InMemoryGrantStore.class, StoreFactory.grantStore(null, MAPPER));
        assertInstanceOf(InMemoryBudgetStore.class, StoreFactory.budgetStore(null, MAPPER));
        assertInstanceOf(InMemoryEventStore.class, StoreFactory.eventStore(null));
    }

    @Test
    void buildsFirestoreBackedStoresWhenFirestoreIsPresent() {
        // A mock is enough: the constructors only retain the reference, so this asserts the
        // branch is wired correctly without needing live GCP credentials.
        Firestore firestore = mock(Firestore.class);
        assertInstanceOf(FirestoreUserDirectory.class, StoreFactory.userDirectory(firestore));
        assertInstanceOf(FirestoreAccountDirectory.class, StoreFactory.accountDirectory(firestore));
        assertInstanceOf(FirestoreCalculationStore.class, StoreFactory.calculationStore(firestore, MAPPER));
        assertInstanceOf(FirestoreGrantStore.class, StoreFactory.grantStore(firestore, MAPPER));
        assertInstanceOf(FirestoreBudgetStore.class, StoreFactory.budgetStore(firestore, MAPPER));
        assertInstanceOf(FirestoreEventStore.class, StoreFactory.eventStore(firestore));
    }
}
