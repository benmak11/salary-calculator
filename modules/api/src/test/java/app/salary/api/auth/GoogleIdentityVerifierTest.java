package app.salary.api.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdentityVerifierTest {

    private static final String[] FAKE_ISSUERS = {"https://test.local/google", "test.local/google-alt"};
    private static final String AUDIENCE = "test-client-id.apps.googleusercontent.com";
    private static final String KID = "test-key-1";

    private static RSAPublicKey publicKey;
    private static RSAPrivateKey privateKey;
    private static JwkProvider jwks;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        publicKey  = (RSAPublicKey)  pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        Jwk jwk = jwkFromPublicKey(KID, publicKey);
        jwks = singleKeyProvider(jwk);
    }

    private GoogleIdentityVerifier verifier() {
        return new GoogleIdentityVerifier(FAKE_ISSUERS, AUDIENCE, jwks);
    }

    private static String sign(JwtBuilder build) throws Exception {
        return build.apply(JWT.create()
                .withKeyId(KID)
                .withIssuer(FAKE_ISSUERS[0])
                .withAudience(AUDIENCE)
                .withSubject("109876543210987654321")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .withIssuedAt(new Date()))
                .sign(Algorithm.RSA256(publicKey, privateKey));
    }

    @FunctionalInterface
    interface JwtBuilder {
        com.auth0.jwt.JWTCreator.Builder apply(com.auth0.jwt.JWTCreator.Builder b) throws Exception;
    }

    @Test
    void verifiesHappyPath() throws Exception {
        String token = sign(b -> b
                .withClaim("email", "user@gmail.com")
                .withClaim("email_verified", true)
                .withClaim("name", "Alex Carter"));
        VerifiedGoogleIdentity id = verifier().verify(token);
        assertEquals("109876543210987654321", id.googleSub());
        assertEquals("user@gmail.com", id.email());
        assertEquals("Alex Carter", id.name());
        assertNotNull(id);
    }

    @Test
    void acceptsEitherConfiguredIssuer() throws Exception {
        String token = JWT.create()
                .withKeyId(KID)
                .withIssuer(FAKE_ISSUERS[1])
                .withAudience(AUDIENCE)
                .withSubject("u")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.RSA256(publicKey, privateKey));
        VerifiedGoogleIdentity id = verifier().verify(token);
        assertEquals("u", id.googleSub());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = sign(b -> b.withExpiresAt(new Date(System.currentTimeMillis() - 10_000)));
        assertThrows(GoogleVerificationException.class, () -> verifier().verify(token));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        String token = JWT.create()
                .withKeyId(KID)
                .withIssuer(FAKE_ISSUERS[0])
                .withAudience("some-other-client-id.apps.googleusercontent.com")
                .withSubject("u")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.RSA256(publicKey, privateKey));
        assertThrows(GoogleVerificationException.class, () -> verifier().verify(token));
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        String token = JWT.create()
                .withKeyId(KID)
                .withIssuer("https://evil.example")
                .withAudience(AUDIENCE)
                .withSubject("u")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.RSA256(publicKey, privateKey));
        assertThrows(GoogleVerificationException.class, () -> verifier().verify(token));
    }

    @Test
    void rejectsTokenSignedByUnknownKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair rogue = gen.generateKeyPair();
        String token = JWT.create()
                .withKeyId("rogue")
                .withIssuer(FAKE_ISSUERS[0])
                .withAudience(AUDIENCE)
                .withSubject("u")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.RSA256((RSAPublicKey) rogue.getPublic(), (RSAPrivateKey) rogue.getPrivate()));
        assertThrows(GoogleVerificationException.class, () -> verifier().verify(token));
    }

    @Test
    void rejectsBlankInput() {
        GoogleIdentityVerifier v = verifier();
        assertThrows(GoogleVerificationException.class, () -> v.verify(""));
        assertThrows(GoogleVerificationException.class, () -> v.verify(null));
        assertThrows(GoogleVerificationException.class, () -> v.verify("not.a.jwt"));
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
