package app.salary.api.store;

/**
 * Firestore collection, subcollection and field names, in one place.
 *
 * <p>These were previously redeclared per store: {@code "users"} appeared in four files and
 * {@code "accountId"} in three, so a layout change meant finding every copy and trusting
 * that you had. They are also the exact strings the pending B-1b migration re-keys, and a
 * dual-write decorator has to name both the old and new path — which is unworkable while
 * each path fragment is private to the store that uses it.
 *
 * <p><b>These are storage identifiers, not API surface.</b> They deliberately live in the
 * api module rather than in {@code common}: a Firestore field name is not something a DTO
 * shared with clients should know about. Renaming one here silently orphans live production
 * documents, so treat any change as a data migration rather than a refactor.
 */
public final class StoreConstants {

    // Top-level collections
    public static final String USERS = "users";
    public static final String ACCOUNTS = "accounts";
    public static final String IDENTITIES = "identities";
    public static final String CHECK_INS = "checkIns";
    public static final String ENTITLEMENTS = "entitlements";
    public static final String LINK_CODES = "linkCodes";
    public static final String EVENTS = "events";

    // Subcollections under a user or account document
    public static final String CALCULATIONS = "calculations";
    public static final String GRANTS = "grants";
    public static final String BUDGET = "budget";
    public static final String ENTRIES = "entries";
    public static final String STORES = "stores";

    /** The budget is one document per user, not a list, so its id is fixed. */
    public static final String BUDGET_DOC_ID = "current";

    // Document fields
    public static final String FIELD_ACCOUNT_ID = "accountId";
    public static final String FIELD_SUB = "sub";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_EXPIRES_AT = "expiresAt";
    public static final String FIELD_ATTEMPTS = "attempts";
    public static final String FIELD_REDEEMED = "redeemed";
    public static final String FIELD_GRANT = "grant";
    public static final String FIELD_CHECK_IN = "checkIn";
    public static final String FIELD_PAY_DATE = "payDate";
    public static final String FIELD_LEGACY_PRO_BUDGET = "legacy_pro_budget";
    public static final String FIELD_ID = "id";
    public static final String FIELD_LAST_SEEN_AT = "lastSeenAt";
    public static final String FIELD_DISPLAY_NAME = "displayName";
    public static final String FIELD_UPDATED_AT = "updatedAt";
    public static final String FIELD_PRODUCT_ID = "productId";
    public static final String FIELD_REVOKED = "revoked";
    public static final String FIELD_SUMMARY = "summary";
    public static final String FIELD_REQUEST = "request";
    public static final String FIELD_RESPONSE = "response";

    private StoreConstants() {
    }
}
