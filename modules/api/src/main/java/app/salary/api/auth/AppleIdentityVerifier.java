package app.salary.api.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * Verifies Apple ID identity tokens.
 *
 * Checks signature against Apple's JWKS, issuer, audience, expiry, and the SHA-256
 * binding between the client-supplied raw nonce and the {@code nonce} claim.
 */
public class AppleIdentityVerifier {
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL_STR = "https://appleid.apple.com/auth/keys";

    private final String expectedIssuer;
    private final String expectedAudience;
    private final JwkProvider keys;

    /** Production constructor — fetches and caches Apple's live JWKS. */
    public AppleIdentityVerifier(String expectedAudience) {
        this(APPLE_ISSUER, expectedAudience, buildAppleKeyProvider());
    }

    /** Constructor allowing a custom issuer and key provider — used by tests with a local JWKS. */
    public AppleIdentityVerifier(String expectedIssuer, String expectedAudience, JwkProvider keys) {
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.keys = keys;
    }

    public VerifiedAppleIdentity verify(String identityToken, String rawNonce) throws AppleVerificationException {
        if (identityToken == null || identityToken.isBlank()) {
            throw new AppleVerificationException("Missing identity token");
        }
        if (rawNonce == null || rawNonce.isBlank()) {
            throw new AppleVerificationException("Missing nonce");
        }

        DecodedJWT decoded;
        try {
            decoded = JWT.decode(identityToken);
        } catch (JWTDecodeException e) {
            throw new AppleVerificationException("Token structure invalid", e);
        }

        Algorithm algorithm;
        try {
            Jwk jwk = keys.get(decoded.getKeyId());
            algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
        } catch (JwkException e) {
            throw new AppleVerificationException("Unknown Apple signing key", e);
        }

        try {
            JWT.require(algorithm)
                    .withIssuer(expectedIssuer)
                    .withAudience(expectedAudience)
                    .withClaim("nonce", sha256Hex(rawNonce))
                    .build()
                    .verify(decoded);
        } catch (JWTVerificationException e) {
            throw new AppleVerificationException("Token verification failed: " + e.getMessage(), e);
        }

        String sub = decoded.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AppleVerificationException("Subject missing");
        }
        String email = JwtClaims.string(decoded, "email");
        boolean emailVerified = JwtClaims.bool(decoded, "email_verified");
        return new VerifiedAppleIdentity(sub, email, emailVerified);
    }

    private static JwkProvider buildAppleKeyProvider() {
        try {
            URL url = URI.create(APPLE_JWKS_URL_STR).toURL();
            return new JwkProviderBuilder(url)
                    .cached(10, 5, TimeUnit.MINUTES)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Apple JWKS URL", e);
        }
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
