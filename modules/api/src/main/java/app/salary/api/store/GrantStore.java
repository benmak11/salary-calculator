package app.salary.api.store;

import app.salary.common.dto.RsuGrant;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for a user's RSU grants. One implementation per environment —
 * {@link FirestoreGrantStore} in prod, {@link InMemoryGrantStore} in tests
 * and when {@code ENABLE_GCP=false}.
 */
public interface GrantStore {

    /** Oldest-first list of the user's grants. */
    List<RsuGrant> list(String userId);

    /** Persists a new grant under a freshly generated id, which it sets on the grant. */
    RsuGrant create(String userId, RsuGrant grant);

    /**
     * Writes a grant at the id it already carries, creating or replacing.
     *
     * <p>The B-1b dual-write mirror needs this: {@link #create} would mint a second id, and
     * {@link #update} refuses to create a document that does not exist yet, so neither can
     * mirror a grant at its existing id.
     */
    RsuGrant put(String userId, RsuGrant grant);

    /** Replaces an existing grant. Empty when no grant with that id exists. */
    Optional<RsuGrant> update(String userId, String grantId, RsuGrant grant);

    /** Returns true when the grant existed and was removed. */
    boolean delete(String userId, String grantId);

    /** Removes every grant for the user (account deletion). Returns the number removed. */
    int deleteAll(String userId);
}
