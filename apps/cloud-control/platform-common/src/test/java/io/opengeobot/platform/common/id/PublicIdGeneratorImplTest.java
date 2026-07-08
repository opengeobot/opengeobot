/*
 * Function: Public ID generator unit tests — ULID-based prefixed identifiers
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.id;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PublicIdGeneratorImpl}. Verifies ULID format, prefix
 * separation, uniqueness, and monotonic sortability.
 */
class PublicIdGeneratorImplTest {

    private static final Pattern ULID_PATTERN = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    private final PublicIdGeneratorImpl generator = new PublicIdGeneratorImpl();

    @Test
    void generate_prependsPrefixWithUnderscore() {
        String id = generator.generate("mission");

        assertNotNull(id);
        assertTrue(id.startsWith("mission_"), "ID should start with prefix_");
        String ulidPart = id.substring("mission_".length());
        assertTrue(ULID_PATTERN.matcher(ulidPart).matches(),
                "Suffix should be a valid 26-char ULID, got: " + ulidPart);
    }

    @Test
    void generate_emptyPrefixProducesUnderscoreAndUlid() {
        String id = generator.generate("");

        assertNotNull(id);
        assertTrue(id.startsWith("_"));
        String ulidPart = id.substring(1);
        assertTrue(ULID_PATTERN.matcher(ulidPart).matches());
    }

    @Test
    void generate_differentPrefixesProduceDifferentFormats() {
        String missionId = generator.generate("mission");
        String robotId = generator.generate("rbt");
        String policyId = generator.generate("pol");

        assertTrue(missionId.startsWith("mission_"));
        assertTrue(robotId.startsWith("rbt_"));
        assertTrue(policyId.startsWith("pol_"));
        assertNotEquals(missionId, robotId);
        assertNotEquals(missionId, policyId);
    }

    @RepeatedTest(100)
    void generate_producesUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String id = generator.generate("evt");
            assertTrue(ids.add(id), "Duplicate ID generated: " + id);
        }
    }

    @Test
    void generate_ulidsAreMonotonicallySortable() {
        String first = generator.generate("seq");
        try { Thread.sleep(1); } catch (InterruptedException ignored) { }
        String second = generator.generate("seq");

        String firstUlid = first.substring("seq_".length());
        String secondUlid = second.substring("seq_".length());

        assertTrue(firstUlid.compareTo(secondUlid) <= 0,
                "ULIDs should be monotonically sortable: " + firstUlid + " <= " + secondUlid);
    }

    @Test
    void generate_ulidIs26Chars() {
        String id = generator.generate("test");

        String ulidPart = id.substring("test_".length());
        assertEquals(26, ulidPart.length(), "ULID should be 26 characters");
    }
}
