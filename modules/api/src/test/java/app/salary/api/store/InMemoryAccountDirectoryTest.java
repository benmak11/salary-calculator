package app.salary.api.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static app.salary.api.store.AccountDirectory.PROVIDER_APPLE;
import static app.salary.api.store.AccountDirectory.PROVIDER_GOOGLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAccountDirectoryTest {

    private AccountDirectory accounts;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountDirectory();
    }

    @Test
    void mintsAnAccountOnFirstSight() {
        String accountId = accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        assertEquals(26, accountId.length());
        assertEquals(Optional.of(accountId), accounts.findAccountId(PROVIDER_APPLE, "apple-sub-1"));
    }

    @Test
    void isIdempotentAcrossRepeatSignIns() {
        String first = accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        String second = accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        String third = accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", null);
        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    void keepsProvidersInTheirOwnNamespace() {
        // Providers can independently issue the same sub string; they are different people.
        String apple = accounts.resolveOrCreate(PROVIDER_APPLE, "shared-sub", "Ben");
        String google = accounts.resolveOrCreate(PROVIDER_GOOGLE, "shared-sub", "Ben");
        assertNotEquals(apple, google);
    }

    @Test
    void doesNotMergeAppleAndGoogleIdentitiesOnItsOwn() {
        // Documents current behaviour rather than endorsing it: merging is a later item,
        // and this test is what will fail loudly when someone implements it.
        String apple = accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        String google = accounts.resolveOrCreate(PROVIDER_GOOGLE, "google-sub-1", "Ben");
        assertNotEquals(apple, google);
    }

    @Test
    void returnsEmptyForAnUnknownIdentity() {
        assertTrue(accounts.findAccountId(PROVIDER_APPLE, "never-seen").isEmpty());
    }

    @Test
    void deleteRemovesTheAccountAndItsIdentity() {
        accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        assertEquals(1, accounts.deleteByProviderSub("apple-sub-1"));
        assertTrue(accounts.findAccountId(PROVIDER_APPLE, "apple-sub-1").isEmpty());
    }

    @Test
    void deleteIsIdempotent() {
        accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        accounts.deleteByProviderSub("apple-sub-1");
        assertEquals(0, accounts.deleteByProviderSub("apple-sub-1"));
    }

    @Test
    void deleteReturnsZeroForAnUnknownSub() {
        assertEquals(0, accounts.deleteByProviderSub("never-seen"));
    }

    @Test
    void deleteLeavesOtherAccountsAlone() {
        accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        String survivor = accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-2", "Someone else");
        accounts.deleteByProviderSub("apple-sub-1");
        assertEquals(Optional.of(survivor), accounts.findAccountId(PROVIDER_APPLE, "apple-sub-2"));
    }

    @Test
    void resolveAfterDeleteMintsAFreshAccount() {
        String original = accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        accounts.deleteByProviderSub("apple-sub-1");
        String reborn = accounts.resolveOrCreate(PROVIDER_APPLE, "apple-sub-1", "Ben");
        assertNotEquals(original, reborn,
                "a deleted account must not be resurrected under its old id");
    }

    @Test
    void mintsADistinctAccountPerIdentity() {
        String first = accounts.resolveOrCreate(PROVIDER_APPLE, "a", null);
        String second = accounts.resolveOrCreate(PROVIDER_APPLE, "b", null);
        assertNotEquals(first, second);
        // Ordering across timestamps is a Ulid concern and is asserted in UlidTest; ids minted
        // inside one millisecond are distinguished by entropy alone and have no defined order.
        assertEquals(26, first.length());
        assertEquals(26, second.length());
    }
}
