package app.salary.api.auth;

import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * Optional-claim readers shared by {@link AppleIdentityVerifier} and
 * {@link GoogleIdentityVerifier}.
 *
 * <p>Both providers treat profile claims as optional — Apple only sends {@code email} on the
 * very first authorization, and Google omits {@code name} when the scope was not granted. So
 * every read has to distinguish "absent", "explicitly null" and "present", which
 * {@code getClaim} alone does not: it returns a non-null Claim either way and
 * {@code asString()} on a missing claim yields null indistinguishably from a null value.
 *
 * <p>Lives here rather than as private helpers on each verifier because the two copies were
 * byte-identical, which the duplication detector correctly objected to.
 */
final class JwtClaims {

    private JwtClaims() {
    }

    /** The claim as a string, or null when it is missing or explicitly null. */
    static String string(DecodedJWT jwt, String name) {
        var claim = jwt.getClaim(name);
        return claim.isMissing() || claim.isNull() ? null : claim.asString();
    }

    /**
     * The claim as a boolean, defaulting to false.
     *
     * <p>Absent means false on purpose: these back {@code emailVerified}, where "the provider
     * did not tell us" must not read as verified. {@code asBoolean()} also returns null when
     * the claim holds a non-boolean, which collapses to false for the same reason.
     */
    static boolean bool(DecodedJWT jwt, String name) {
        var claim = jwt.getClaim(name);
        if (claim.isMissing() || claim.isNull()) {
            return false;
        }
        Boolean value = claim.asBoolean();
        return value != null && value;
    }
}
