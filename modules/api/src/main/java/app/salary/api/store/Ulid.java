package app.salary.api.store;

import java.security.SecureRandom;
import java.time.Clock;

/**
 * Minimal ULID generator — a 48-bit millisecond timestamp followed by 80 bits of
 * randomness, rendered as 26 Crockford base32 characters.
 *
 * <p>Chosen over {@link java.util.UUID} for account ids because ULIDs sort
 * lexicographically by creation time, so a Firestore console listing of
 * {@code accounts} reads in the order the accounts were created. That is purely an
 * operational nicety; nothing in the code depends on the ordering.
 *
 * <p>No monotonic counter for ids minted inside the same millisecond: 80 bits of
 * {@link SecureRandom} makes a collision far less likely than the failure modes
 * around it, and strict within-millisecond ordering buys us nothing here.
 */
public final class Ulid {
    /** Crockford base32 — excludes I, L, O and U to survive being read aloud or retyped. */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TIMESTAMP_CHARS = 10;
    private static final int ENTROPY_BYTES = 10;
    private static final int LENGTH = 26;

    private Ulid() {}

    public static String generate() {
        return generate(Clock.systemUTC().millis());
    }

    static String generate(long epochMilli) {
        char[] out = new char[LENGTH];

        long timestamp = epochMilli;
        for (int i = TIMESTAMP_CHARS - 1; i >= 0; i--) {
            out[i] = ALPHABET[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }

        byte[] entropy = new byte[ENTROPY_BYTES];
        RANDOM.nextBytes(entropy);

        // 80 bits divides evenly into 16 five-bit groups, so the buffer always drains.
        int bitBuffer = 0;
        int bitCount = 0;
        int index = TIMESTAMP_CHARS;
        for (byte b : entropy) {
            bitBuffer = (bitBuffer << 8) | (b & 0xFF);
            bitCount += 8;
            while (bitCount >= 5) {
                bitCount -= 5;
                out[index++] = ALPHABET[(bitBuffer >>> bitCount) & 0x1F];
            }
        }

        return new String(out);
    }
}
