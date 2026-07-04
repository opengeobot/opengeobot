/*
 * Function: ULID generator — 128-bit sortable identifier encoded in Crockford Base32
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.id;

import java.security.SecureRandom;

/**
 * Generates ULID (Universally Unique Lexicographically Sortable Identifier) strings.
 * A ULID is 26 characters in Crockford Base32: 10 chars of millisecond timestamp
 * (48 bits) followed by 16 chars of randomness (80 bits). ULIDs are monotonically
 * sortable and used for event IDs, trace IDs and public IDs across the platform.
 */
public final class Ulid {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    public static String next() {
        long timestamp = System.currentTimeMillis();
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        char[] chars = new char[26];

        encodeTimestamp(timestamp, chars);
        encodeRandomness(randomBytes, chars);

        return new String(chars);
    }

    private static void encodeTimestamp(long timestamp, char[] chars) {
        long ts = timestamp;
        for (int i = 9; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (ts & 0x1F));
            ts >>>= 5;
        }
    }

    private static void encodeRandomness(byte[] randomBytes, char[] chars) {
        long high = 0;
        for (int i = 0; i < 5; i++) {
            high = (high << 8) | (randomBytes[i] & 0xFFL);
        }
        for (int i = 17; i >= 10; i--) {
            chars[i] = ALPHABET.charAt((int) (high & 0x1F));
            high >>>= 5;
        }

        long low = 0;
        for (int i = 5; i < 10; i++) {
            low = (low << 8) | (randomBytes[i] & 0xFFL);
        }
        for (int i = 25; i >= 18; i--) {
            chars[i] = ALPHABET.charAt((int) (low & 0x1F));
            low >>>= 5;
        }
    }
}
