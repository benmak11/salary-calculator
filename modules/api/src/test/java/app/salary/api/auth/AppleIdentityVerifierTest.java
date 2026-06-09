package app.salary.api.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppleIdentityVerifierTest {

    private static final String FAKE_ISSUER = "https://test.local/apple";
    private static final String AUDIENCE = "com.benmakusha.incomatic";
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

    private AppleIdentityVerifier verifier() {
        return new AppleIdentityVerifier(FAKE_ISSUER, AUDIENCE, jwks);
    }

    private static String sign(JwtBuilder build) throws Exception {
        return build.apply(JWT.create()
                .withKeyId(KID)
                .withIssuer(FAKE_ISSUER)
                .withAudience(AUDIENCE)
                .withSubject("000123.applesub.4567")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .withIssuedAt(new Date()))
                .sign(Algorithm.RSA256(publicKey, privateKey));
    }

    @FunctionalInterface
    interface JwtBuilder {
        com.auth0.jwt.JWTCreator.Builder apply(com.auth0.jwt.JWTCreator.Builder b) throws Exception;
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void verifiesHappyPath() throws Exception {
        String nonce = "nonce-xyz";
        String token = sign(b -> b
                .withClaim("nonce", sha256Hex(nonce))
                .withClaim("email", "user@privaterelay.appleid.com")
                .withClaim("email_verified", true));
        VerifiedAppleIdentity id = verifier().verify(token, nonce);
        assertEquals("000123.applesub.4567", id.appleSub());
        assertEquals("user@privaterelay.appleid.com", id.email());
        assertNotNull(id);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = sign(b -> b
                .withExpiresAt(new Date(System.currentTimeMillis() - 10_000))
                .withClaim("nonce", sha256Hex("n")));
        assertThrows(AppleVerificationException.class, () -> verifier().verify(token, "n"));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        String token = JWT.create()
                .withKeyId(KID)
                .withIssuer(FAKE_ISSUER)
                .withAudience("com.someone.else")
                .withSubject("u")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .withClaim("nonce", sha256Hex("n"))
                .sign(Algorithm.RSA256(publicKey, privateKey));
        assertThrows(AppleVerificationException.class, () -> verifier().verify(token, "n"));
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        String token = JWT.create()
                .withKeyId(KID)
                .withIssuer("https://evil.example")
                .withAudience(AUDIENCE)
                .withSubject("u")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .withClaim("nonce", sha256Hex("n"))
                .sign(Algorithm.RSA256(publicKey, privateKey));
        assertThrows(AppleVerificationException.class, () -> verifier().verify(token, "n"));
    }

    @Test
    void rejectsMismatchedNonce() throws Exception {
        String token = sign(b -> b.withClaim("nonce", sha256Hex("the-real-nonce")));
        assertThrows(AppleVerificationException.class,
                () -> verifier().verify(token, "a-different-nonce"));
    }

    @Test
    void rejectsTokenSignedByUnknownKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair rogue = gen.generateKeyPair();
        String token = JWT.create()
                .withKeyId("rogue")
                .withIssuer(FAKE_ISSUER)
                .withAudience(AUDIENCE)
                .withSubject("u")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .withClaim("nonce", sha256Hex("n"))
                .sign(Algorithm.RSA256((RSAPublicKey) rogue.getPublic(), (RSAPrivateKey) rogue.getPrivate()));
        assertThrows(AppleVerificationException.class, () -> verifier().verify(token, "n"));
    }

    @Test
    void rejectsBlankInputs() {
        AppleIdentityVerifier v = verifier();
        assertThrows(AppleVerificationException.class, () -> v.verify("", "n"));
        assertThrows(AppleVerificationException.class, () -> v.verify(null, "n"));
        assertThrows(AppleVerificationException.class, () -> v.verify("not.a.jwt", ""));
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
        // BigInteger.toByteArray() prepends a 0x00 sign byte for positive values
        // whose high bit would otherwise look negative. JWK encoding omits that.
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
