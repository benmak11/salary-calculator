package app.salary.api.store;

import app.salary.common.dto.RsuGrant;

import java.util.List;
import java.util.Optional;

/**
 * B-1b Phase 1 decorator for {@link GrantStore}. See {@link DualWriteStore}.
 *
 * <p>Ids are kept in step: the authoritative side mints one and the mirror is written with
 * {@link GrantStore#put} at that same id. Calling {@code create} on the mirror would mint a
 * second, different id for the same grant.
 */
public class DualWriteGrantStore extends DualWriteStore<GrantStore> implements GrantStore {

    public DualWriteGrantStore(GrantStore subKeyed, GrantStore accountKeyed,
                               AccountIdResolver resolver, boolean readAccountKeyed) {
        super(subKeyed, accountKeyed, resolver, readAccountKeyed);
    }

    @Override
    public List<RsuGrant> list(String userId) {
        Sides<GrantStore> s = sides(userId);
        return s.authoritative().list(s.key());
    }

    @Override
    public RsuGrant create(String userId, RsuGrant grant) {
        Sides<GrantStore> s = sides(userId);
        RsuGrant created = s.authoritative().create(s.key(), grant);
        s.alsoWrite("grant.create", (store, key) -> store.put(key, created));
        return created;
    }

    @Override
    public RsuGrant put(String userId, RsuGrant grant) {
        Sides<GrantStore> s = sides(userId);
        RsuGrant written = s.authoritative().put(s.key(), grant);
        s.alsoWrite("grant.put", (store, key) -> store.put(key, grant));
        return written;
    }

    @Override
    public Optional<RsuGrant> update(String userId, String grantId, RsuGrant grant) {
        Sides<GrantStore> s = sides(userId);
        Optional<RsuGrant> updated = s.authoritative().update(s.key(), grantId, grant);
        // Mirrored with put, not update: the mirror may not hold this document yet if the
        // grant was created before dual-write was switched on, and update refuses to create.
        updated.ifPresent(g -> s.alsoWrite("grant.update", (store, key) -> store.put(key, g)));
        return updated;
    }

    @Override
    public boolean delete(String userId, String grantId) {
        Sides<GrantStore> s = sides(userId);
        boolean deleted = s.authoritative().delete(s.key(), grantId);
        s.alsoWrite("grant.delete", (store, key) -> store.delete(key, grantId));
        return deleted;
    }

    @Override
    public int deleteAll(String userId) {
        Sides<GrantStore> s = sides(userId);
        int removed = s.authoritative().deleteAll(s.key());
        s.alsoWrite("grant.deleteAll", GrantStore::deleteAll);
        return removed;
    }
}
