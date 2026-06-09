package app.salary.api.auth;

/** Thrown when an Apple identity token fails verification. */
public class AppleVerificationException extends Exception {
    public AppleVerificationException(String message) {
        super(message);
    }

    public AppleVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
