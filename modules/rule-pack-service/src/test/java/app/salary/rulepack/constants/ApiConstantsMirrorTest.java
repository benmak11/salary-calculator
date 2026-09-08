package app.salary.rulepack.constants;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This service deliberately does not depend on modules/common, so it keeps its own copy of
 * the shared wire constants. That copy was previously kept in step by a comment asking
 * nicely, which is fine for three values and not fine for fifteen — and the ones that drift
 * silently are the worst kind: a health path or a request-id header that disagrees between
 * two services fails in production, not in a build.
 *
 * <p>Reads the other module's <em>source</em> rather than its class, because depending on
 * the class is exactly the coupling being avoided.
 */
class ApiConstantsMirrorTest {

    private static final Pattern CONSTANT =
            Pattern.compile("String\\s+(\\w+)\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*;");

    @Test
    void everySharedConstantHasTheSameValueInBothCopies() {
        Map<String, String> common = parse(locateCommonSource());
        assertNotNull(common.get("ERROR"), "did not parse the common ApiConstants source");

        int compared = 0;
        for (Field f : ApiConstants.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) {
                continue;
            }
            String theirs = common.get(f.getName());
            if (theirs == null) {
                // Not every constant is shared; only the ones present in both must agree.
                continue;
            }
            String ours;
            try {
                ours = (String) f.get(null);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            assertEquals(theirs, ours,
                    "ApiConstants." + f.getName() + " has drifted from modules/common");
            compared++;
        }
        // Guards against the test passing because it compared nothing at all.
        assertEquals(common.size() > 0, compared > 0, "no shared constants were compared");
    }

    private static Map<String, String> parse(Path source) {
        Map<String, String> values = new HashMap<>();
        try {
            Matcher m = CONSTANT.matcher(Files.readString(source));
            while (m.find()) {
                values.put(m.group(1), m.group(2));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return values;
    }

    /**
     * Walks up from the working directory rather than assuming one, so this behaves the same
     * whether Gradle runs it from the module or the root. Fails loudly when the file moves —
     * a mirror test that quietly skips is worse than no mirror test.
     */
    private static Path locateCommonSource() {
        Path relative = Paths.get("modules", "common", "src", "main", "java", "app", "salary",
                "common", "constants", "ApiConstants.java");
        for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        fail("could not find modules/common ApiConstants.java from " + Paths.get("").toAbsolutePath());
        throw new IllegalStateException("unreachable");
    }
}
