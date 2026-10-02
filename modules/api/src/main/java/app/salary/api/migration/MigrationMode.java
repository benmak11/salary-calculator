package app.salary.api.migration;

/**
 * Which layout the stores are wired to, derived from the two B-1b flags.
 *
 * <p>A named enum rather than nested conditionals in {@code Main} because the three states
 * are a <em>sequence</em>, and the one that is easy to get wrong is the last: turning
 * dual-write off is not "no migration wiring". Leaving the stores sub-keyed at that point
 * serves a layout that stopped being written, so every user's recent data appears to vanish.
 * That mistake reached production once, on 2026-10-01, and was caught only because a guard
 * refused to boot.
 *
 * <p>{@code Main} is excluded from coverage as bootstrap wiring, which is exactly why this
 * decision does not live there.
 */
public enum MigrationMode {

    /** Before phase 1, and again after phase 6. The original layout. */
    SUB_KEYED_ONLY,

    /** Phases 1-4: both layouts written. The read flag picks which one serves reads. */
    DUAL_WRITE,

    /** Phase 5 onward: account-keyed only, no decorator. The sub-keyed layout goes stale. */
    ACCOUNT_KEYED_ONLY;

    /**
     * @param hasFirestore false for local dev and tests, where the in-memory stores have no
     *                     layout at all and the flags are meaningless rather than wrong.
     */
    public static MigrationMode from(boolean dualWrite, boolean readAccountKeyed, boolean hasFirestore) {
        if (!hasFirestore) {
            return SUB_KEYED_ONLY;
        }
        if (dualWrite) {
            return DUAL_WRITE;
        }
        return readAccountKeyed ? ACCOUNT_KEYED_ONLY : SUB_KEYED_ONLY;
    }
}
