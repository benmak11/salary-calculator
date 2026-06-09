package app.salary.api.auth;

/** Outcome of a successful Apple identity-token verification. */
public record VerifiedAppleIdentity(String appleSub, String email, boolean emailVerified) {
}
