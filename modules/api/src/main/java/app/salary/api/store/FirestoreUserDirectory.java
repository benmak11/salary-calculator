package app.salary.api.store;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class FirestoreUserDirectory implements UserDirectory {
    private static final Logger log = LoggerFactory.getLogger(FirestoreUserDirectory.class);
    private static final String COLLECTION = "users";

    private final Firestore firestore;

    public FirestoreUserDirectory(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void upsertOnSignIn(String userId, String displayName, String email) {
        DocumentReference ref = firestore.collection(COLLECTION).document(userId);
        Map<String, Object> patch = new HashMap<>();
        patch.put("id", userId);
        patch.put("lastSeenAt", Timestamp.now());
        if (displayName != null && !displayName.isBlank()) {
            patch.put("displayName", displayName);
        }
        if (email != null && !email.isBlank()) {
            patch.put("email", email);
        }
        try {
            DocumentSnapshot existing = ref.get().get();
            if (!existing.exists()) {
                patch.put("createdAt", Timestamp.now());
            }
            ref.set(patch, SetOptions.merge()).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore user upsert interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore user upsert failed", e);
        }
    }

    @Override
    public Optional<String> displayName(String userId) {
        try {
            DocumentSnapshot snap = firestore.collection(COLLECTION).document(userId).get().get();
            if (!snap.exists()) return Optional.empty();
            String name = snap.getString("displayName");
            return (name == null || name.isBlank()) ? Optional.empty() : Optional.of(name);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore user fetch interrupted", ie);
        } catch (ExecutionException e) {
            log.warn("Firestore user fetch failed for {}", userId, e);
            return Optional.empty();
        }
    }
}
