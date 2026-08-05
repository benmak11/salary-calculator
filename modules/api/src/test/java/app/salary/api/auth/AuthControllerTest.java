package app.salary.api.auth;

import app.salary.api.store.AccountDirectory;
import app.salary.api.store.InMemoryAccountDirectory;
import app.salary.api.store.UserDirectory;
import app.salary.api.validation.RequestValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Returns a canned verified identity (incl. an email) without touching Apple's JWKS. */
    private static final class StubAppleVerifier extends AppleIdentityVerifier {
        private final VerifiedAppleIdentity identity;
        StubAppleVerifier(VerifiedAppleIdentity identity) {
            super("test-audience");   // JWKS is fetched lazily on verify(), which we override
            this.identity = identity;
        }
        @Override
        public VerifiedAppleIdentity verify(String identityToken, String rawNonce) {
            return identity;
        }
    }

    /** Returns a canned verified identity (incl. an email) without touching Google's JWKS. */
    private static final class StubGoogleVerifier extends GoogleIdentityVerifier {
        private final VerifiedGoogleIdentity identity;
        StubGoogleVerifier(VerifiedGoogleIdentity identity) {
            super("test-audience");   // JWKS is fetched lazily on verify(), which we override
            this.identity = identity;
        }
        @Override
        public VerifiedGoogleIdentity verify(String idToken) {
            return identity;
        }
    }

    /** Records exactly what the controller asks the directory to persist. */
    private static final class RecordingUserDirectory implements UserDirectory {
        String userId;
        String displayName;
        boolean upserted;

        @Override
        public void upsertOnSignIn(String userId, String displayName) {
            this.userId = userId;
            this.displayName = displayName;
            this.upserted = true;
        }

        @Override
        public Optional<String> displayName(String userId) {
            return Optional.ofNullable(displayName);
        }

        /**
         * Placeholder override method from @UserDirectory interface. Not used in this test.
         * @param userId - userId
         */
        @Override
        public void delete(String userId) {
            // no-op: account deletion is not exercised by these auth tests
        }
    }

    private Javalin app(AppleIdentityVerifier appleVerifier, GoogleIdentityVerifier googleVerifier, UserDirectory users) {
        AccountDirectory accounts = new InMemoryAccountDirectory();
        byte[] secret = new byte[32];
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) i;
        SessionTokenService tokens = new SessionTokenService(secret);
        AuthController controller = new AuthController(appleVerifier, googleVerifier, tokens, users,
                accounts, new RequestValidator());
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            controller.register(config.routes);
        });
    }

    @Test
    void signIn_persistsIdAndDisplayName_butNeverTheEmail() {
        // Apple's verified identity DOES carry an email here...
        VerifiedAppleIdentity identity =
                new VerifiedAppleIdentity("apple-sub-123", "leak@example.com", true);
        RecordingUserDirectory users = new RecordingUserDirectory();

        JavalinTest.test(app(new StubAppleVerifier(identity), null, users), (server, client) -> {
            var resp = client.post("/v1/auth/apple", Map.of(
                    "identityToken", "tok",
                    "nonce", "n",
                    "displayName", "Alex Carter"));
            assertEquals(200, resp.code());

            JsonNode json = MAPPER.readTree(resp.body().string());
            assertNotNull(json.get("sessionToken"));
            assertEquals("apple-sub-123", json.get("user").get("id").asText());
            assertEquals("Alex Carter", json.get("user").get("displayName").asText());
            // The email must never surface in the response either.
            assertFalse(json.toString().contains("leak@example.com"),
                    "sign-in response must not include the Apple email");
        });

        // ...but the directory only ever receives the id + display name. UserDirectory has
        // no email parameter, so there is no path for the email to be persisted. If a future
        // change re-introduces email storage, this spy won't compile — a deliberate tripwire.
        assertTrue(users.upserted);
        assertEquals("apple-sub-123", users.userId);
        assertEquals("Alex Carter", users.displayName);
    }

    @Test
    void googleSignIn_persistsIdAndDisplayName_butNeverTheEmail() {
        // Google's verified identity DOES carry an email here...
        VerifiedGoogleIdentity identity =
                new VerifiedGoogleIdentity("google-sub-456", "leak@example.com", true, "Alex Carter");
        RecordingUserDirectory users = new RecordingUserDirectory();

        JavalinTest.test(app(null, new StubGoogleVerifier(identity), users), (server, client) -> {
            var resp = client.post("/v1/auth/google", Map.of("idToken", "tok"));
            assertEquals(200, resp.code());

            JsonNode json = MAPPER.readTree(resp.body().string());
            assertNotNull(json.get("sessionToken"));
            assertEquals("google-sub-456", json.get("user").get("id").asText());
            // No displayName supplied by the client, so it falls back to the token's name claim.
            assertEquals("Alex Carter", json.get("user").get("displayName").asText());
            assertFalse(json.toString().contains("leak@example.com"),
                    "sign-in response must not include the Google email");
        });

        assertTrue(users.upserted);
        assertEquals("google-sub-456", users.userId);
        assertEquals("Alex Carter", users.displayName);
    }
}
