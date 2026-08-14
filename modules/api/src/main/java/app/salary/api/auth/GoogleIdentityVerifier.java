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
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;

/**
 * Verifies Google ID tokens.
 *
 * Checks signature against Google's JWKS, issuer, audience, and expiry. Unlike Apple's flow,
 * Android's Credential Manager Google Sign-In doesn't thread a client-generated nonce into the
 * token, so there is no nonce claim to check here.
 */
public class GoogleIdentityVerifier {
    private static final String[] GOOGLE_ISSUERS = {"accounts.google.com", "https://accounts.google.com"};
    private static final String GOOGLE_JWTS_URL_STR = "https://www.googleapis.com/oauth2/v3/certs";

    private final String[] expectedIssuers;
    private final String expectedAudience;
    private final JwkProvider keys;

    /** Production constructor — fetches and caches Google's live JWKS. */
    public GoogleIdentityVerifier(String expectedAudience) {
        this(GOOGLE_ISSUERS, expectedAudience, buildGoogleKeyProvider());
    }

    /** Constructor allowing custom issuers and a key provider — used by tests with a local JWKS. */
    public GoogleIdentityVerifier(String[] expectedIssuers, String expectedAudience, JwkProvider keys) {
        this.expectedIssuers = expectedIssuers;
        this.expectedAudience = expectedAudience;
        this.keys = keys;
    }

    public VerifiedGoogleIdentity verify(String idToken) throws GoogleVerificationException {
        if (idToken == null || idToken.isBlank()) {
            throw new GoogleVerificationException("Missing identity token");
        }

        DecodedJWT decoded;
        try {
            decoded = JWT.decode(idToken);
        } catch (JWTDecodeException e) {
            throw new GoogleVerificationException("Token structure invalid", e);
        }

        Algorithm algorithm;
        try {
            Jwk jwk = keys.get(decoded.getKeyId());
            algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
        } catch (JwkException e) {
            throw new GoogleVerificationException("Unknown Google signing key", e);
        }

        try {
            JWT.require(algorithm)
                    .withIssuer(expectedIssuers)
                    .withAudience(expectedAudience)
                    .build()
                    .verify(decoded);
        } catch (JWTVerificationException e) {
            throw new GoogleVerificationException("Token verification failed: " + e.getMessage(), e);
        }

        String sub = decoded.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new GoogleVerificationException("Subject missing");
        }
        String email = stringClaim(decoded, "email");
        boolean emailVerified = boolClaim(decoded);
        String name = stringClaim(decoded, "name");
        return new VerifiedGoogleIdentity(sub, email, emailVerified, name);
    }

    private static JwkProvider buildGoogleKeyProvider() {
        try {
            URL url = URI.create(GOOGLE_JWTS_URL_STR).toURL();
            return new JwkProviderBuilder(url)
                    .cached(10, 5, TimeUnit.MINUTES)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Google JWKS URL", e);
        }
    }

    private static String stringClaim(DecodedJWT jwt, String name) {
        var claim = jwt.getClaim(name);
        return claim.isMissing() || claim.isNull() ? null : claim.asString();
    }

    private static boolean boolClaim(DecodedJWT jwt) {
        var claim = jwt.getClaim("email_verified");
        if (claim.isMissing() || claim.isNull()) return false;
        Boolean b = claim.asBoolean();
        return b != null && b;
    }
}
