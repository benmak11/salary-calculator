package app.salary.api.version;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed {@code X-Incomatic-Client} header — a platform plus a semantic version,
 * e.g. {@code ios/1.9.0} or {@code android/1.0.0}.
 *
 * <p>Parsing is deliberately lenient and never throws. This value only ever decides whether
 * a request is <em>blocked</em>, so anything unrecognisable resolves to "unknown client" and
 * is let through. A stricter parser would turn a client's malformed header into an outage
 * for that client, which is the exact failure this mechanism exists to prevent.
 */
public record ClientVersion(String platform, int major, int minor, int patch) {

    public static final String IOS = "ios";
    public static final String ANDROID = "android";

    /** {@code major[.minor[.patch[.build]]]}, each component bounded to six digits. */
    private static final String VERSION = "\\d{1,6}(?:\\.\\d{1,6}){0,3}";

    /**
     * {@code platform/major[.minor[.patch]]}, with anything after the version ignored so a
     * client can append a build number or a suffix ({@code ios/1.9.0 (42)}) without breaking.
     *
     * <p>The trailing {@code (?![\d.])} is what stops a run of digits longer than the bound
     * being <em>truncated</em> into a plausible version: without it {@code ios/1234567890}
     * matches its first six digits and reads as {@code 123456.0.0}. A number we cannot
     * represent must resolve to "unknown client", not to a different number.
     */
    private static final Pattern HEADER = Pattern.compile(
            "^\\s*([A-Za-z][A-Za-z0-9_-]{0,19})\\s*/\\s*(" + VERSION + ")(?![\\d.])");

    private static final Pattern BARE_VERSION = Pattern.compile("^\\s*(" + VERSION + ")\\s*$");

    /** Parses a full header value such as {@code ios/1.9.0}. Empty when unrecognisable. */
    public static Optional<ClientVersion> parse(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        Matcher m = HEADER.matcher(header);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(fromComponents(m.group(1), m.group(2)));
    }

    /**
     * Parses a bare version for a known platform, e.g. {@code ("ios", "1.9.0")}. Used for the
     * configured minimums, which carry their platform in the env var name rather than the value.
     */
    public static Optional<ClientVersion> parse(String platform, String version) {
        if (platform == null || platform.isBlank() || version == null || version.isBlank()) {
            return Optional.empty();
        }
        Matcher m = BARE_VERSION.matcher(version);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(fromComponents(platform, m.group(1)));
    }

    /**
     * A fourth component is parsed and then dropped: some Android builds carry one, and the
     * gate only ever needs to order major/minor/patch.
     */
    private static ClientVersion fromComponents(String platform, String version) {
        String[] parts = version.split("\\.");
        return new ClientVersion(
                platform.toLowerCase(Locale.ROOT),
                Integer.parseInt(parts[0]),
                parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
                parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
    }

    /**
     * Compares version numbers only — the caller is responsible for having matched platforms
     * first. Comparing {@code ios/2.0.0} against {@code android/3.0.0} is meaningless, so this
     * intentionally does not implement {@link Comparable}: there is no total order across
     * platforms and pretending otherwise invites a silent mis-gate.
     */
    public boolean isOlderThan(ClientVersion other) {
        if (major != other.major) return major < other.major;
        if (minor != other.minor) return minor < other.minor;
        return patch < other.patch;
    }

    /** {@code 1.9.0} — the form shown to users and written to logs. */
    public String displayVersion() {
        return major + "." + minor + "." + patch;
    }

    @Override
    public String toString() {
        return platform + "/" + displayVersion();
    }
}
