package app.salary.api.service;

import app.salary.api.store.AccountDirectory;
import app.salary.api.store.Entitlement;
import app.salary.api.store.InMemoryAccountDirectory;
import app.salary.api.store.InMemoryEntitlementStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
    private static final Instant FUTURE = NOW.plus(30, ChronoUnit.DAYS);
    private static final Instant PAST = NOW.minus(1, ChronoUnit.DAYS);

    private InMemoryEntitlementStore entitlements;
    private InMemoryAccountDirectory accounts;

    @BeforeEach
    void setUp() {
        entitlements = new InMemoryEntitlementStore();
        accounts = new InMemoryAccountDirectory();
    }

    private EntitlementService service(boolean subscriptionEnforcement, boolean playEnforcement) {
        return new EntitlementService(entitlements, accounts, subscriptionEnforcement, playEnforcement);
    }

    private static Entitlement appStore(Instant expiresAt, boolean revoked) {
        return new Entitlement(Entitlement.APP_STORE, "pro_monthly", expiresAt, revoked, NOW);
    }

    private static Entitlement play(Instant expiresAt, boolean revoked) {
        return new Entitlement(Entitlement.PLAY, "pro_monthly", expiresAt, revoked, NOW);
    }

    @Test
    void aLiveEntitlementFromAnyStoreGrantsPro() {
        entitlements.upsert("acct-1", appStore(FUTURE, false));
        assertTrue(service(true, true).isPro("acct-1", NOW));
    }

    @Test
    void anExpiredEntitlementDoesNotGrantPro() {
        entitlements.upsert("acct-1", appStore(PAST, false));
        assertFalse(service(true, true).isPro("acct-1", NOW));
    }

    @Test
    void revocationBeatsAFutureExpiry() {
        // A refunded subscription can still carry a future expiry. Honouring it would hand
        // Pro to everyone who charged back.
        entitlements.upsert("acct-1", appStore(FUTURE, true));
        assertFalse(service(true, true).isPro("acct-1", NOW));
    }

    @Test
    void playRecordsAreIgnoredUntilPlayEnforcementIsOn() {
        entitlements.upsert("acct-1", play(FUTURE, false));
        assertFalse(service(true, false).isPro("acct-1", NOW), "a half-built Play integration must not grant Pro");
        assertTrue(service(true, true).isPro("acct-1", NOW));
    }

    @Test
    void anUnknownAccountIsNotPro() {
        EntitlementService service = service(true, true);
        assertFalse(service.isPro("nobody", NOW));
        assertFalse(service.isPro(null, NOW));
        assertFalse(service.isPro("  ", NOW));
    }

    @Test
    void enforcementOffAdmitsEveryoneButStillResolves() {
        // The shipping default: the gate runs, records what it would have done, and lets the
        // request through.
        EntitlementService service = service(false, false);
        assertTrue(service.allows(Optional.of("acct-1"), EntitlementService.FEATURE_BUDGET_PLAN, NOW));
        assertTrue(service.allows(Optional.empty(), EntitlementService.FEATURE_BUDGET_PLAN, NOW));
    }

    @Test
    void enforcementOnRefusesAnAccountWithNoEntitlement() {
        assertFalse(service(true, true).allows(Optional.of("acct-1"),
                EntitlementService.FEATURE_BUDGET_PLAN, NOW));
    }

    @Test
    void grandfatheredBudgetUsersKeepBudgetEvenUnderEnforcement() {
        String accountId = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "sub-1", "Alex");
        accounts.grantLegacyProBudget(accountId);

        EntitlementService service = service(true, true);
        assertTrue(service.allows(Optional.of(accountId), EntitlementService.FEATURE_BUDGET_PLAN, NOW));
        // One feature, not Pro: the wall still stands everywhere else.
        assertFalse(service.isPro(accountId, NOW));
        assertFalse(service.allows(Optional.of(accountId), "ytd_tracker", NOW));
    }

    @Test
    void mergeKeepsTheLaterExpiryPerStore() {
        Instant later = FUTURE.plus(60, ChronoUnit.DAYS);
        entitlements.upsert("target", appStore(FUTURE, false));
        entitlements.upsert("source", appStore(later, false));

        assertEquals(1, service(true, true).mergeInto("source", "target"));
        assertEquals(later, entitlements.findAll("target").getFirst().expiresAt());
    }

    @Test
    void mergeDoesNotShortenAnExistingSubscription() {
        Instant earlier = NOW.plus(1, ChronoUnit.DAYS);
        entitlements.upsert("target", appStore(FUTURE, false));
        entitlements.upsert("source", appStore(earlier, false));

        assertEquals(0, service(true, true).mergeInto("source", "target"));
        assertEquals(FUTURE, entitlements.findAll("target").getFirst().expiresAt());
    }

    @Test
    void mergeCarriesAStoreTheTargetDoesNotHave() {
        // The cross-platform case: bought on Android, signing in on iPhone.
        entitlements.upsert("target", appStore(PAST, false));
        entitlements.upsert("source", play(FUTURE, false));

        assertEquals(1, service(true, true).mergeInto("source", "target"));
        List<Entitlement> after = entitlements.findAll("target");
        assertEquals(2, after.size(), "both stores' records must survive the merge");
        assertTrue(service(true, true).isPro("target", NOW));
    }

    @Test
    void aRevokedRecordNeverOverwritesALiveOne() {
        entitlements.upsert("target", appStore(FUTURE, false));
        entitlements.upsert("source", appStore(FUTURE.plus(60, ChronoUnit.DAYS), true));

        assertEquals(0, service(true, true).mergeInto("source", "target"));
        assertTrue(service(true, true).isPro("target", NOW),
                "a refund on one device must not cancel a good subscription on another");
    }

    @Test
    void mergingIntoYourselfOrFromNothingIsANoOp() {
        EntitlementService service = service(true, true);
        entitlements.upsert("acct-1", appStore(FUTURE, false));
        assertEquals(0, service.mergeInto("acct-1", "acct-1"));
        assertEquals(0, service.mergeInto("empty", "acct-1"));
        assertEquals(0, service.mergeInto(null, "acct-1"));
    }

    @Test
    void oneStoreWriteDoesNotDisturbTheOther() {
        entitlements.upsert("acct-1", appStore(FUTURE, false));
        entitlements.upsert("acct-1", play(FUTURE, false));
        entitlements.upsert("acct-1", appStore(PAST, false));

        assertEquals(2, entitlements.findAll("acct-1").size());
        assertTrue(service(true, true).isPro("acct-1", NOW), "the Play record must still grant access");
    }
}
