package app.salary.api.auth;

import app.salary.api.store.InMemoryUserDirectory;
import app.salary.api.store.UserDirectory;
import app.salary.api.validation.RequestValidator;
import app.salary.api.validation.ValidationException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AuthFlowTest {

    private static final String ISSUER = "https://test.local/apple";
    private static final String AUDIENCE = "com.benmakusha.incomatic";
    private static final String GOOGLE_ISSUER = "https://test.local/google";
    private static final String GOOGLE_AUDIENCE = "test-client-id.apps.googleusercontent.com";
    private static final String KID = "k1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static JwkProvider jwks;

    @BeforeAll
    static void setUpKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        publicKey  = (RSAPublicKey)  pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        jwks = singleKeyProvider(jwkFromPublicKey(KID, publicKey));
    }

    private static byte[] sessionSecret() {
        byte[] s = new byte[32];
        for (int i = 0; i < s.length; i++) s[i] = (byte) (i + 1);
        return s;
    }

    private static String signAppleToken(String nonce, String sub) throws Exception {
        return JWT.create()
                .withKeyId(KID)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject(sub)
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .withIssuedAt(new Date())
                .withClaim("nonce", sha256Hex(nonce))
                .sign(Algorithm.RSA256(publicKey, privateKey));
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private static String signGoogleToken(String sub) {
        return JWT.create()
                .withKeyId(KID)
                .withIssuer(GOOGLE_ISSUER)
                .withAudience(GOOGLE_AUDIENCE)
                .withSubject(sub)
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .withIssuedAt(new Date())
                .withClaim("name", "Ben Makusha")
                .sign(Algorithm.RSA256(publicKey, privateKey));
    }

    private Javalin app() {
        AppleIdentityVerifier appleVerifier = new AppleIdentityVerifier(ISSUER, AUDIENCE, jwks);
        GoogleIdentityVerifier googleVerifier =
                new GoogleIdentityVerifier(new String[] {GOOGLE_ISSUER}, GOOGLE_AUDIENCE, jwks);
        SessionTokenService sessionTokens = new SessionTokenService(sessionSecret());
        UserDirectory users = new InMemoryUserDirectory();
        AuthMiddleware middleware = new AuthMiddleware(sessionTokens);
        AuthController controller =
                new AuthController(appleVerifier, googleVerifier, sessionTokens, users, new RequestValidator());

        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(MAPPER, false));
            config.startup.showJavalinBanner = false;
            config.routes.before(middleware::handle);
            config.routes.exception(ValidationException.class, (e, ctx) ->
                    ctx.status(HttpStatus.BAD_REQUEST).json(e.getErrors()));
            controller.register(config.routes);
            // Probe route that echoes back the authenticated userId (or empty if unauthenticated).
            config.routes.get("/_probe/whoami", ctx -> ctx.json(Map.of(
                    "userId", AuthMiddleware.currentUserId(ctx).orElse(""))));
        });
    }

    @Test
    void signInIssuesTokenThatTheMiddlewareThenAccepts() throws Exception {
        String identityToken = signAppleToken("nonce-abc", "000123.apple.sub");

        JavalinTest.test(app(), (server, client) -> {
            String body = MAPPER.writeValueAsString(Map.of(
                    "identityToken", identityToken,
                    "nonce", "nonce-abc",
                    "displayName", "Ben Makusha"));
            var resp = client.post("/v1/auth/apple", body);
            assertEquals(200, resp.code());
            JsonNode response = MAPPER.readTree(resp.body().string());
            String session = response.get("sessionToken").asText();
            assertNotEquals("", session);
            assertEquals("000123.apple.sub", response.get("user").get("id").asText());
            assertEquals("Ben Makusha", response.get("user").get("displayName").asText());

            // Use that session token as Bearer credentials against the probe.
            var who = client.get("/_probe/whoami", req ->
                    req.header("Authorization", "Bearer " + session));
            assertEquals(200, who.code());
            JsonNode whoBody = MAPPER.readTree(who.body().string());
            assertEquals("000123.apple.sub", whoBody.get("userId").asText());
        });
    }

    @Test
    void signInWith401OnInvalidIdentityToken() {
        JavalinTest.test(app(), (server, client) -> {
            String body = MAPPER.writeValueAsString(Map.of(
                    "identityToken", "this-is-not-a-jwt",
                    "nonce", "nonce-abc"));
            var resp = client.post("/v1/auth/apple", body);
            assertEquals(401, resp.code());
        });
    }

    @Test
    void signInWith400OnMissingFields() {
        JavalinTest.test(app(), (server, client) -> {
            var resp = client.post("/v1/auth/apple", Map.of());
            assertEquals(400, resp.code());
        });
    }

    @Test
    void googleSignInIssuesTokenThatTheMiddlewareThenAccepts() {
        String idToken = signGoogleToken("109876543210987654321");

        JavalinTest.test(app(), (server, client) -> {
            String body = MAPPER.writeValueAsString(Map.of("idToken", idToken));
            var resp = client.post("/v1/auth/google", body);
            assertEquals(200, resp.code());
            JsonNode response = MAPPER.readTree(resp.body().string());
            String session = response.get("sessionToken").asText();
            assertNotEquals("", session);
            assertEquals("109876543210987654321", response.get("user").get("id").asText());
            // No displayName supplied by the client, so it falls back to the token's name claim.
            assertEquals("Ben Makusha", response.get("user").get("displayName").asText());

            var who = client.get("/_probe/whoami", req ->
                    req.header("Authorization", "Bearer " + session));
            assertEquals(200, who.code());
            JsonNode whoBody = MAPPER.readTree(who.body().string());
            assertEquals("109876543210987654321", whoBody.get("userId").asText());
        });
    }

    @Test
    void googleSignInWith401OnInvalidIdentityToken() {
        JavalinTest.test(app(), (server, client) -> {
            String body = MAPPER.writeValueAsString(Map.of("idToken", "this-is-not-a-jwt"));
            var resp = client.post("/v1/auth/google", body);
            assertEquals(401, resp.code());
        });
    }

    @Test
    void googleSignInWith400OnMissingFields() {
        JavalinTest.test(app(), (server, client) -> {
            var resp = client.post("/v1/auth/google", Map.of());
            assertEquals(400, resp.code());
        });
    }

    @Test
    void middlewareIgnoresMissingHeader() {
        JavalinTest.test(app(), (server, client) -> {
            var resp = client.get("/_probe/whoami");
            assertEquals(200, resp.code());
            JsonNode body = MAPPER.readTree(resp.body().string());
            assertEquals("", body.get("userId").asText());
        });
    }

    @Test
    void middlewareIgnoresInvalidBearer() {
        JavalinTest.test(app(), (server, client) -> {
            var resp = client.get("/_probe/whoami",
                    req -> req.header("Authorization", "Bearer not-a-real-token"));
            assertEquals(200, resp.code());
            JsonNode body = MAPPER.readTree(resp.body().string());
            assertEquals("", body.get("userId").asText());
        });
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static Jwk jwkFromPublicKey(String kid, RSAPublicKey key) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("kty", "RSA");
        attrs.put("kid", kid);
        attrs.put("alg", "RS256");
        attrs.put("use", "sig");
        attrs.put("n", base64UrlNoPad(stripSignByte(key.getModulus().toByteArray())));
        attrs.put("e", base64UrlNoPad(stripSignByte(key.getPublicExponent().toByteArray())));
        return Jwk.fromValues(attrs);
    }

    private static byte[] stripSignByte(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] out = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, out, 0, out.length);
            return out;
        }
        return bytes;
    }

    private static String base64UrlNoPad(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static JwkProvider singleKeyProvider(Jwk jwk) {
        return new JwkProvider() {
            @Override
            public Jwk get(String keyId) throws JwkException {
                if (jwk.getId().equals(keyId)) return jwk;
                throw new com.auth0.jwk.SigningKeyNotFoundException(
                        "No key with id " + keyId, null);
            }
        };
    }
}
