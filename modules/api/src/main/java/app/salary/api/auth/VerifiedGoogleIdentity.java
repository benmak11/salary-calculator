package app.salary.api.auth;

/** Outcome of a successful Google identity-token verification. */
public record VerifiedGoogleIdentity(String googleSub, String email, boolean emailVerified, String name) {
}
