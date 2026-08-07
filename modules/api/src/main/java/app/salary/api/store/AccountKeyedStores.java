package app.salary.api.store;

/**
 * The stores keyed on <b>accountId</b>, which is every collection created since the identity
 * schema landed. The migration never has to touch these, which is the whole reason B-1a
 * shipped before them.
 *
 * <p>Any field may be null when its backing store is not configured; account deletion treats
 * a missing store as nothing to purge rather than an error.
 */
public record AccountKeyedStores(
        EntitlementStore entitlements,
        LinkCodeStore linkCodes,
        CheckInStore checkIns) {
}
