package app.salary.api.store;

import app.salary.common.dto.PaycheckCheckIn;

import java.util.List;
import java.util.Optional;

/**
 * Stores confirmed paydays, keyed on <b>accountId</b> rather than provider sub.
 *
 * <p>That is the whole reason B-1a landed first: a collection created against a provider sub
 * has to be migrated twice. {@code calculations}, {@code grants} and {@code budget} predate
 * the identity schema and are still sub-keyed; this one is not, so the pending migration
 * never has to touch it.
 *
 * <p>One check-in per payday. Confirming the same date again replaces the previous entry
 * rather than accumulating duplicates — a user correcting a typo is the expected case, and a
 * second row for the same payday would double-count in every YTD total built on top.
 */
public interface CheckInStore {

    /** Creates or replaces the check-in for its pay date. Returns the stored entry. */
    PaycheckCheckIn save(String accountId, PaycheckCheckIn checkIn);

    /** Newest payday first, capped at {@code limit}. */
    List<PaycheckCheckIn> list(String accountId, int limit);

    Optional<PaycheckCheckIn> find(String accountId, String id);

    /** True when something was removed. */
    boolean delete(String accountId, String id);

    /** Removes every check-in for the account. Used by account deletion. */
    int deleteAll(String accountId);
}
