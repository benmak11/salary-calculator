package app.salary.api.client;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/**
 * Mints Google-signed ID tokens for service-to-service auth on Cloud Run.
 * The underlying {@link IdTokenCredentials} caches and auto-refreshes tokens,
 * so {@link #get()} is cheap on the hot path.
 *
 * On Cloud Run, ADC resolves to the bound service account (which implements
 * {@link IdTokenProvider}). On a developer machine, {@code gcloud auth
 * application-default login} produces user credentials that do NOT implement
 * {@link IdTokenProvider} — construction fails fast there so production
 * misconfiguration is obvious instead of silently degrading.
 */
public final class GoogleIdTokenSupplier implements Supplier<String> {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenSupplier.class);

    private final IdTokenCredentials credentials;

    public GoogleIdTokenSupplier(String audience) {
        try {
            GoogleCredentials base = GoogleCredentials.getApplicationDefault();
            if (!(base instanceof IdTokenProvider provider)) {
                throw new IllegalStateException(
                        "Application Default Credentials do not support ID tokens (got "
                                + base.getClass().getSimpleName()
                                + "). Expected to run on Cloud Run with a bound service account.");
            }
            this.credentials = IdTokenCredentials.newBuilder()
                    .setIdTokenProvider(provider)
                    .setTargetAudience(audience)
                    .build();
            log.info("ID token supplier initialized for audience: {}", audience);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load Application Default Credentials", e);
        }
    }

    @Override
    public String get() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to refresh ID token", e);
        }
    }
}
