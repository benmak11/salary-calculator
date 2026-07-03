package app.salary.api.store;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryUserDirectoryTest {

    private final InMemoryUserDirectory directory = new InMemoryUserDirectory();

    @Test
    void upsertOnSignIn_firstSignIn_storesDisplayName() {
        directory.upsertOnSignIn("user-1", "Ada");

        assertEquals(Optional.of("Ada"), directory.displayName("user-1"));
    }

    @Test
    void upsertOnSignIn_secondSignIn_keepsOriginalDisplayName() {
        // Apple only sends the name on the FIRST sign-in; later sign-ins must not erase it.
        directory.upsertOnSignIn("user-1", "Ada");
        directory.upsertOnSignIn("user-1", null);

        assertEquals(Optional.of("Ada"), directory.displayName("user-1"));
    }

    @Test
    void upsertOnSignIn_backfillsNameWhenFirstSignInHadNone() {
        directory.upsertOnSignIn("user-1", null);
        directory.upsertOnSignIn("user-1", "Ada");

        assertEquals(Optional.of("Ada"), directory.displayName("user-1"));
    }

    @Test
    void displayName_unknownUserOrNoName_isEmpty() {
        directory.upsertOnSignIn("nameless", null);

        assertTrue(directory.displayName("unknown").isEmpty());
        assertTrue(directory.displayName("nameless").isEmpty());
    }

    @Test
    void delete_removesUser() {
        directory.upsertOnSignIn("user-1", "Ada");

        directory.delete("user-1");

        assertTrue(directory.displayName("user-1").isEmpty());
    }
}
