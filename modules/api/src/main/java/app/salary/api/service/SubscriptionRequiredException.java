package app.salary.api.service;

import java.io.Serial;

/**
 * Raised when a Pro-only route is reached without a live entitlement. Rendered as
 * <b>402 Payment Required</b> by the handler in {@code Main.createApp}.
 *
 * <p>402 rather than 403 because the clients map it to a specific outcome: the roadmap has
 * both platforms translating 402 into {@code SubscriptionRequired(feature)} and showing the
 * paywall for that feature. A 403 would be indistinguishable from a permissions problem.
 *
 * <p>Carries the feature name so the paywall can open on the right surface instead of a
 * generic upgrade page.
 */
public class SubscriptionRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String feature;

    public SubscriptionRequiredException(String feature) {
        super("Subscription required");
        this.feature = feature;
    }

    public String getFeature() {
        return feature;
    }
}
