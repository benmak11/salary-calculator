package app.salary.api.auth;

/** Thrown when a Google identity token fails verification. */
public class GoogleVerificationException extends Exception {
    public GoogleVerificationException(String message) {
        super(message);
    }

    public GoogleVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
