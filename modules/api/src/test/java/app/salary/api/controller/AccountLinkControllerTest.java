package app.salary.api.controller;

import app.salary.api.auth.AuthMiddleware;
import app.salary.api.auth.SessionTokenService;
import app.salary.api.ratelimit.RateLimiter;
import app.salary.api.service.EntitlementService;
import app.salary.api.store.AccountDirectory;
import app.salary.api.store.Entitlement;
import app.salary.api.store.InMemoryAccountDirectory;
import app.salary.api.store.InMemoryEntitlementStore;
import app.salary.api.store.InMemoryLinkCodeStore;
import app.salary.api.store.LinkCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountLinkControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

    private InMemoryAccountDirectory accounts;
    private InMemoryEntitlementStore entitlements;
    private InMemoryLinkCodeStore linkCodes;
    private SessionTokenService sessionTokens;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountDirectory();
        entitlements = new InMemoryEntitlementStore();
        linkCodes = new InMemoryLinkCodeStore();
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) i;
        sessionTokens = new SessionTokenService(secret);
    }

    private Javalin app() {
        // A limiter generous enough not to interfere with the behavioural tests.
        return app(new RateLimiter(600, 600, 100));
    }

    private Javalin app(RateLimiter redeemLimiter) {
        AuthMiddleware middleware = new AuthMiddleware(sessionTokens);
        EntitlementService service = new EntitlementService(entitlements, accounts, false, true);
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            config.routes.before(middleware::handle);
            new AccountLinkController(linkCodes, accounts, service, redeemLimiter)
                    .register(config.routes);
        });
    }

    private Consumer<io.javalin.testtools.Request.Builder> as(String sub) {
        return r -> r.header("Authorization", "Bearer " + sessionTokens.mint(sub).token());
    }

    @Test
    void generatesASixDigitCodeForASignedInAccount() {
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/account/link-code", null, as("apple-1"));
            assertEquals(201, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.get("code").asText().matches("\\d{6}"));
            assertFalse(body.get("expiresAt").asText().isBlank());
        });
    }

    @Test
    void bothRoutesRefuseAnonymousCallers() {
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(401, client.post("/v1/account/link-code", null).code());
            assertEquals(401, client.post("/v1/account/link", Map.of("code", "123456")).code());
        });
    }

    @Test
    void generatingTwiceReplacesTheOutstandingCode() {
        // At most one live code per account, so repeated taps do not widen the guessable set.
        String accountId = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");
        String first = linkCodes.issue(accountId).code();
        String second = linkCodes.issue(accountId).code();

        assertNotEquals(first, second);
        assertTrue(linkCodes.find(first).isEmpty(), "the superseded code must not still redeem");
    }

    @Test
    void redeemingRepointsTheSecondIdentityAtTheFirstAccount() {
        String targetAccountId = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");
        String code = linkCodes.issue(targetAccountId).code();

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/account/link", Map.of("code", code), as("google-1"));
            assertEquals(200, response.code());

            JsonNode body = MAPPER.readTree(response.body().string());
            assertTrue(body.get("linked").asBoolean());
            assertEquals(targetAccountId, body.get("accountId").asText());
            // The whole point: one human, one accountId.
            assertEquals(targetAccountId,
                    accounts.findAccountId(AccountDirectory.PROVIDER_GOOGLE, "google-1").orElseThrow());
        });
    }

    @Test
    void redeemingMergesEntitlementsKeepingTheLaterExpiry() {
        String target = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");
        String source = accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");
        Instant later = NOW.plus(90, ChronoUnit.DAYS);
        entitlements.upsert(target, new Entitlement(Entitlement.APP_STORE, "pro", NOW.plus(1, ChronoUnit.DAYS), false, NOW));
        entitlements.upsert(source, new Entitlement(Entitlement.PLAY, "pro", later, false, NOW));
        String code = linkCodes.issue(target).code();

        JavalinTest.test(app(), (server, client) -> {
            var response = client.post("/v1/account/link", Map.of("code", code), as("google-1"));
            assertEquals(200, response.code());
            assertEquals(1, MAPPER.readTree(response.body().string()).get("mergedEntitlementStores").asInt());
            assertEquals(2, entitlements.findAll(target).size(), "both stores must survive");
        });
    }

    @Test
    void aCodeIsSingleUse() {
        String target = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-2", "Sam");
        String code = linkCodes.issue(target).code();

        JavalinTest.test(app(), (server, client) -> {
            assertEquals(200, client.post("/v1/account/link", Map.of("code", code), as("google-1")).code());
            // A second person must not ride the same code onto someone else's account.
            assertEquals(404, client.post("/v1/account/link", Map.of("code", code), as("google-2")).code());
        });
    }

    @Test
    void repeatedGuessesAgainstOneCodeBurnIt() {
        // Narrow but real threat: someone who saw part of a code on a screen and is
        // hammering the rest. It does NOT cover enumeration of the whole space, because an
        // attacker doing that tries a different code each time and never accumulates
        // attempts against any one. The redeem rate limiter is what bounds that.
        String target = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");
        String code = linkCodes.issue(target).code();

        for (int i = 0; i < LinkCode.MAX_ATTEMPTS; i++) {
            linkCodes.recordFailedAttempt(code);
        }

        JavalinTest.test(app(), (server, client) ->
                assertEquals(404, client.post("/v1/account/link", Map.of("code", code), as("google-1")).code(),
                        "a burned code must not redeem even when the digits are right"));
    }

    @Test
    void aWrongGuessIsIndistinguishableFromAnExpiredOrUnknownCode() {
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");
        JavalinTest.test(app(), (server, client) ->
                assertEquals(404, client.post("/v1/account/link", Map.of("code", "999999"), as("google-1")).code(),
                        "distinguishing them would tell a guesser which codes exist"));
    }

    @Test
    void anExpiredCodeIsRejected() {
        var clock = new MutableClock(NOW);
        linkCodes = new InMemoryLinkCodeStore(clock);
        String target = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");
        String code = linkCodes.issue(target).code();

        clock.advance(Duration.ofMinutes(11));

        JavalinTest.test(app(), (server, client) ->
                assertEquals(404, client.post("/v1/account/link", Map.of("code", code), as("google-1")).code()));
    }

    @Test
    void malformedCodesAreRejectedWithoutTouchingTheStore() {
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");
        JavalinTest.test(app(), (server, client) -> {
            assertEquals(400, client.post("/v1/account/link", Map.of("code", "12345"), as("google-1")).code());
            assertEquals(400, client.post("/v1/account/link", Map.of("code", "abcdef"), as("google-1")).code());
            assertEquals(400, client.post("/v1/account/link", Map.of(), as("google-1")).code());
        });
    }

    @Test
    void spacingInACopiedCodeIsTolerated() {
        String target = accounts.resolveOrCreate(AccountDirectory.PROVIDER_APPLE, "apple-1", "Alex");
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");
        String code = linkCodes.issue(target).code();
        String spaced = code.substring(0, 3) + " " + code.substring(3);

        JavalinTest.test(app(), (server, client) ->
                assertEquals(200, client.post("/v1/account/link", Map.of("code", spaced), as("google-1")).code()));
    }

    @Test
    void generatingWithNoAccountRecordAsksForAnotherSignIn() {
        // Pre-schema users have no account yet; this must not 500.
        JavalinTest.test(app(), (server, client) ->
                assertEquals(409, client.post("/v1/account/link-code", null, as("never-mapped")).code()));
    }

    /** Lets expiry be tested without sleeping for ten minutes. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void redemptionIsThrottledPerCaller() {
        // This limiter, not the per-code counter, is what bounds enumeration of the code
        // space: a sweep tries a different code each time and never burns any single one.
        accounts.resolveOrCreate(AccountDirectory.PROVIDER_GOOGLE, "google-1", "Alex");

        JavalinTest.test(app(new RateLimiter(1, 1, 100)), (server, client) -> {
            assertEquals(404, client.post("/v1/account/link", Map.of("code", "111111"), as("google-1")).code());
            var throttled = client.post("/v1/account/link", Map.of("code", "222222"), as("google-1"));
            assertEquals(429, throttled.code());
            assertNotNull(throttled.headers().get("Retry-After"), "clients need a backoff hint");
        });
    }
}
