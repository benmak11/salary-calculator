package app.salary.api.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;

/**
 * Picks the Firestore or in-memory implementation of each store.
 *
 * <p>Extracted from {@code Main} so the one rule that governs all of them — a null
 * {@link Firestore} means run in memory — lives in a single place, and so {@code Main}
 * depends on the store interfaces rather than on every implementation.
 *
 * <p>Still hand-wired: this is a factory, not a container. Nothing here scans, registers or
 * resolves anything, and {@code Main} remains the only place dependencies are assembled.
 */
public final class StoreFactory {

    private StoreFactory() {}

    public static UserDirectory userDirectory(Firestore firestore) {
        return firestore != null ? new FirestoreUserDirectory(firestore) : new InMemoryUserDirectory();
    }

    public static AccountDirectory accountDirectory(Firestore firestore) {
        return firestore != null ? new FirestoreAccountDirectory(firestore) : new InMemoryAccountDirectory();
    }

    public static CalculationStore calculationStore(Firestore firestore, ObjectMapper mapper) {
        return firestore != null ? new FirestoreCalculationStore(firestore, mapper) : new InMemoryCalculationStore();
    }

    public static GrantStore grantStore(Firestore firestore, ObjectMapper mapper) {
        return firestore != null ? new FirestoreGrantStore(firestore, mapper) : new InMemoryGrantStore();
    }

    public static BudgetStore budgetStore(Firestore firestore, ObjectMapper mapper) {
        return firestore != null ? new FirestoreBudgetStore(firestore, mapper) : new InMemoryBudgetStore();
    }

    public static EntitlementStore entitlementStore(Firestore firestore) {
        return firestore != null ? new FirestoreEntitlementStore(firestore) : new InMemoryEntitlementStore();
    }

    public static LinkCodeStore linkCodeStore(Firestore firestore) {
        return firestore != null ? new FirestoreLinkCodeStore(firestore) : new InMemoryLinkCodeStore();
    }

    public static CheckInStore checkInStore(Firestore firestore, ObjectMapper mapper) {
        return firestore != null ? new FirestoreCheckInStore(firestore, mapper) : new InMemoryCheckInStore();
    }

    public static EventStore eventStore(Firestore firestore) {
        return firestore != null ? new FirestoreEventStore(firestore) : new InMemoryEventStore();
    }
}
