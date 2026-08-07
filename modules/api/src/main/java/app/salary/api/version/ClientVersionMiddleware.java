package app.salary.api.version;

import app.salary.common.constants.ApiConstants;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code X-Incomatic-Client} and, once minimums are configured, turns away clients too
 * old to be supported.
 *
 * <p><b>The timing is the whole point.</b> A version gate can only ever act on clients that
 * already send the header, so this has to be in the field — on the backend and in a shipped
 * iOS and Android release — <em>before</em> any minimum is set. Deployed with no minimums it
 * is pure observation: every request is recorded with its client platform and version and
 * nothing is blocked. Enforcement is a later config change, not a later code change.
 *
 * <p>Unknown clients are never blocked. A missing or unparseable header means curl, the
 * marketing site, a health probe, or a client whose header we got wrong — none of which
 * should be locked out by a mechanism aimed at old app builds.
 *
 * <p>{@code /v1/events} is exempt even under enforcement. Blocking analytics from old clients
 * would destroy the measurement of how many old clients are still out there, which is the
 * number that says whether raising the minimum is safe.
 */
public class ClientVersionMiddleware {
    private static final Logger log = LoggerFactory.getLogger(ClientVersionMiddleware.class);

    public static final String CLIENT_HEADER = "X-Incomatic-Client";

    private static final String ACTUATOR_PREFIX = "/actuator";
    private static final String EVENTS_PATH = "/v1/events";

    /** Platform (lowercase) to the oldest version still allowed. Absent platform = not enforced. */
    private final Map<String, ClientVersion> minimums;

    public ClientVersionMiddleware(Map<String, ClientVersion> minimums) {
        this.minimums = minimums == null ? Map.of() : Map.copyOf(minimums);
    }

    public void handle(Context ctx) {
        Optional<ClientVersion> parsed = ClientVersion.parse(ctx.header(CLIENT_HEADER));
        if (parsed.isEmpty()) {
            return;
        }
        ClientVersion client = parsed.get();

        // Recorded on every request, enforced or not: the access log is where the old-client
        // population becomes visible, and that has to work before any minimum is set.
        MDC.put(ApiConstants.MDC_CLIENT_PLATFORM, client.platform());
        MDC.put(ApiConstants.MDC_CLIENT_VERSION, client.displayVersion());

        if (isExempt(ctx.path())) {
            return;
        }
        ClientVersion minimum = minimums.get(client.platform());
        if (minimum == null || !client.isOlderThan(minimum)) {
            return;
        }

        // Rendered once and reused by the log line and the body, rather than being rebuilt
        // per call site.
        String currentVersion = client.displayVersion();
        String minimumVersion = minimum.displayVersion();

        log.info("client below minimum supported version: platform={} version={} minimum={}",
                client.platform(), currentVersion, minimumVersion);
        throw new UpgradeRequiredException(
                upgradeBody(client.platform(), currentVersion, minimumVersion));
    }

    private static boolean isExempt(String path) {
        return path.startsWith(ACTUATOR_PREFIX) || EVENTS_PATH.equals(path);
    }

    /**
     * Structured so the client can render its own upgrade screen rather than parsing prose.
     * {@code error} is the stable contract; {@code message} is a fallback for a client that
     * has no screen for this yet, which is every client that predates the gate.
     */
    private static Map<String, String> upgradeBody(String platform, String currentVersion,
                                                   String minimumVersion) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put(ApiConstants.ERROR, "upgrade_required");
        body.put("message", "This version of Incomatic is no longer supported. "
                + "Update to the latest version to continue.");
        body.put("platform", platform);
        body.put("currentVersion", currentVersion);
        body.put("minimumVersion", minimumVersion);
        return body;
    }
}
