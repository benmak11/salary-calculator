package app.salary.api.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Mints and validates HS256 session JWTs we hand to the iOS client after a successful
 * Apple sign-in. Owning our own token (rather than re-presenting the Apple identity token)
 * keeps the trust boundary inside this service and avoids paying for JWKS verification
 * on every authenticated request.
 */
public class SessionTokenService {
    public static final Duration DEFAULT_TTL = Duration.ofDays(30);
    private static final String ISSUER = "salary-calculator-api";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration ttl;
    private final Clock clock;

    public SessionTokenService(byte[] secret, Duration ttl, Clock clock) {
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("Session secret must be at least 32 bytes");
        }
        this.algorithm = Algorithm.HMAC256(secret.clone());
        // The verifier always uses the system clock for expiry; expired-token tests bake
        // the past `exp` into the token at mint time via the constructor clock parameter.
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
        this.ttl = ttl;
        this.clock = clock;
    }

    public SessionTokenService(byte[] secret) {
        this(secret, DEFAULT_TTL, Clock.systemUTC());
    }

    public IssuedSession mint(String userId) {
        Instant now = clock.instant();
        Instant expiry = now.plus(ttl);
        String token = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(userId)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(algorithm);
        return new IssuedSession(token, expiry);
    }

    /** Returns the userId carried by the token if it is valid; empty if malformed, tampered, or expired. */
    public Optional<String> validate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            DecodedJWT jwt = verifier.verify(token);
            String sub = jwt.getSubject();
            return (sub == null || sub.isBlank()) ? Optional.empty() : Optional.of(sub);
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }

    public record IssuedSession(String token, Instant expiresAt) {}
}
