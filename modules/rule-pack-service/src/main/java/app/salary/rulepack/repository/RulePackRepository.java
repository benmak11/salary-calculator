package app.salary.rulepack.repository;

import app.salary.rulepack.entity.RulePackEntity;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Hand-rolled blocking Firestore repository for {@link RulePackEntity}.
 *
 * Replaces Spring Cloud GCP's {@code FirestoreReactiveRepository}. We keep the
 * "load all + filter in service" pattern documented in the original migration notes —
 * the collection is expected to stay small enough (&lt;1000 docs) that this avoids
 * composite-index management entirely.
 */
public class RulePackRepository {

    private static final Logger logger = LoggerFactory.getLogger(RulePackRepository.class);
    private static final String COLLECTION = "rule-packs";

    private final Firestore firestore;

    public RulePackRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<RulePackEntity> findAll() {
        try {
            CollectionReference collection = firestore.collection(COLLECTION);
            List<QueryDocumentSnapshot> docs = collection.get().get().getDocuments();
            List<RulePackEntity> result = new ArrayList<>(docs.size());
            for (QueryDocumentSnapshot doc : docs) {
                result.add(fromSnapshot(doc));
            }
            return result;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore findAll interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore findAll failed", e);
        }
    }

    public Optional<RulePackEntity> findById(String id) {
        try {
            DocumentSnapshot snapshot = firestore.collection(COLLECTION).document(id).get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }
            return Optional.of(fromSnapshot(snapshot));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore findById interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore findById failed", e);
        }
    }

    public RulePackEntity save(RulePackEntity entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            throw new IllegalArgumentException("RulePackEntity.id is required for save()");
        }
        try {
            WriteResult result = firestore.collection(COLLECTION)
                    .document(entity.getId())
                    .set(entity)
                    .get();
            logger.debug("Wrote rule pack {} at {}", entity.getId(), result.getUpdateTime());
            return entity;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore save interrupted", ie);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore save failed", e);
        }
    }

    private RulePackEntity fromSnapshot(DocumentSnapshot snapshot) {
        RulePackEntity entity = snapshot.toObject(RulePackEntity.class);
        if (entity != null && entity.getId() == null) {
            entity.setId(snapshot.getId());
        }
        return entity;
    }
}
