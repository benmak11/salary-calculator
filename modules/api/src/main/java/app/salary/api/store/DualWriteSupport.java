package app.salary.api.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared policy for the B-1b dual-write decorators.
 *
 * <p>The rule that matters: <b>the authoritative side's errors propagate, the mirror's do
 * not.</b> Which side is authoritative flips with {@code MIGRATION_READ_ACCOUNT_KEYED}, so
 * the same rule holds in both directions — before the flip a failing mirror is a migration
 * problem and must not break a user's save; after it, the mirror is the old layout being
 * kept warm for rollback, and a failure there must not break a save either.
 *
 * <p>What a swallowed mirror failure costs is a row the parity check will flag, which is
 * exactly what the parity check is for. What propagating it would cost is a user losing
 * their calculation because of a migration they never asked for.
 */
final class DualWriteSupport {
    private static final Logger log = LoggerFactory.getLogger(DualWriteSupport.class);

    private DualWriteSupport() {
    }

    /** Runs a mirror write, logging and absorbing any failure. Never throws. */
    static void mirror(String what, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            // No identifiers and no payload: this is the migration's own bookkeeping, and
            // the logging rules apply to it exactly as they do everywhere else.
            log.warn("dual-write mirror failed: op={} reason={}", what, e.getClass().getSimpleName());
        }
    }
}
