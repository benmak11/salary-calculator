package app.salary.api.version;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientVersionTest {

    @Test
    void parsesTheShippedHeaderFormats() {
        ClientVersion ios = ClientVersion.parse("ios/1.9.0").orElseThrow();
        assertEquals(ClientVersion.IOS, ios.platform());
        assertEquals("1.9.0", ios.displayVersion());

        ClientVersion android = ClientVersion.parse("android/1.0.0").orElseThrow();
        assertEquals(ClientVersion.ANDROID, android.platform());
        assertEquals("1.0.0", android.displayVersion());
    }

    @Test
    void treatsMissingComponentsAsZero() {
        assertEquals("2.0.0", ClientVersion.parse("ios/2").orElseThrow().displayVersion());
        assertEquals("2.1.0", ClientVersion.parse("ios/2.1").orElseThrow().displayVersion());
    }

    @Test
    void toleratesCasingWhitespaceAndTrailingBuildMetadata() {
        // A client that appends a build number must not be treated as an unknown client.
        assertEquals("ios/1.9.0", ClientVersion.parse(" IOS / 1.9.0 (42)").orElseThrow().toString());
        assertEquals("android/1.0.0", ClientVersion.parse("Android/1.0.0-debug").orElseThrow().toString());
    }

    @Test
    void returnsEmptyRatherThanThrowingForJunk() {
        // Every one of these must resolve to "unknown client", which is never blocked.
        for (String junk : new String[]{null, "", "   ", "ios", "1.9.0", "/1.9.0", "ios/", "ios/x.y",
                "9ios/1.0.0", "ios/12345678901234567890"}) {
            assertEquals(Optional.empty(), ClientVersion.parse(junk), "should not parse: " + junk);
        }
    }

    @Test
    void parsesAConfiguredBareMinimum() {
        ClientVersion min = ClientVersion.parse("ios", "1.9.0").orElseThrow();
        assertEquals(ClientVersion.IOS, min.platform());
        assertEquals("1.9.0", min.displayVersion());
        assertEquals("2.0.0", ClientVersion.parse("android", "2").orElseThrow().displayVersion());
    }

    @Test
    void rejectsAMisconfiguredMinimumRatherThanGuessing() {
        // Leaves the platform unenforced upstream, instead of blocking every build.
        assertEquals(Optional.empty(), ClientVersion.parse("ios", "latest"));
        assertEquals(Optional.empty(), ClientVersion.parse("ios", "ios/1.9.0"));
        assertEquals(Optional.empty(), ClientVersion.parse("ios", ""));
        assertEquals(Optional.empty(), ClientVersion.parse("", "1.9.0"));
        assertEquals(Optional.empty(), ClientVersion.parse(null, "1.9.0"));
        assertEquals(Optional.empty(), ClientVersion.parse("ios", null));
    }

    @Test
    void ordersByEachComponentInTurn() {
        ClientVersion minimum = new ClientVersion(ClientVersion.IOS, 1, 9, 0);

        assertTrue(new ClientVersion(ClientVersion.IOS, 0, 99, 99).isOlderThan(minimum));
        assertTrue(new ClientVersion(ClientVersion.IOS, 1, 8, 99).isOlderThan(minimum));
        assertTrue(new ClientVersion(ClientVersion.IOS, 1, 9, 0).isOlderThan(
                new ClientVersion(ClientVersion.IOS, 1, 9, 1)));

        assertFalse(minimum.isOlderThan(minimum), "the minimum itself is supported");
        assertFalse(new ClientVersion(ClientVersion.IOS, 1, 9, 1).isOlderThan(minimum));
        assertFalse(new ClientVersion(ClientVersion.IOS, 2, 0, 0).isOlderThan(minimum));
    }

    @Test
    void doesNotCompareTenAsOlderThanNine() {
        // The string-comparison bug this exists to avoid: "1.10.0" < "1.9.0" lexically.
        assertFalse(new ClientVersion(ClientVersion.IOS, 1, 10, 0)
                .isOlderThan(new ClientVersion(ClientVersion.IOS, 1, 9, 0)));
    }
}
