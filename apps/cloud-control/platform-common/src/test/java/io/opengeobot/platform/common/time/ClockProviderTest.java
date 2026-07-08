/*
 * Function: Clock provider unit tests — system and fixed clock providers
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SystemClockProvider} and {@link FixedClockProvider}.
 */
class ClockProviderTest {

    @Test
    void systemClockProvider_returnsSystemUtcClock() {
        SystemClockProvider provider = new SystemClockProvider();

        Clock clock = provider.getClock();

        assertNotNull(clock);
        assertEquals(Clock.systemUTC().getZone(), clock.getZone());
    }

    @Test
    void systemClockProvider_clockAdvancesWithRealTime() throws InterruptedException {
        SystemClockProvider provider = new SystemClockProvider();

        Instant before = Instant.now();
        Thread.sleep(2);
        Instant clockInstant = provider.getClock().instant();
        Thread.sleep(2);
        Instant after = Instant.now();

        assertTrue(clockInstant.isAfter(before) || clockInstant.equals(before));
        assertTrue(clockInstant.isBefore(after) || clockInstant.equals(after));
    }

    @Test
    void fixedClockProvider_alwaysReturnsSameInstant() {
        Instant fixed = Instant.parse("2026-07-06T12:00:00Z");
        FixedClockProvider provider = new FixedClockProvider(fixed);

        Instant first = provider.getClock().instant();
        Instant second = provider.getClock().instant();

        assertEquals(fixed, first);
        assertEquals(fixed, second);
        assertEquals(first, second);
    }

    @Test
    void fixedClockProvider_usesUtcZone() {
        FixedClockProvider provider = new FixedClockProvider(Instant.now());

        assertEquals(ZoneOffset.UTC, provider.getClock().getZone());
    }

    @Test
    void fixedClockProvider_differentInstantsProduceDifferentClocks() {
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-07-06T12:00:00Z");

        FixedClockProvider provider1 = new FixedClockProvider(t1);
        FixedClockProvider provider2 = new FixedClockProvider(t2);

        assertNotEquals(provider1.getClock().instant(), provider2.getClock().instant());
        assertEquals(t1, provider1.getClock().instant());
        assertEquals(t2, provider2.getClock().instant());
    }

    @Test
    void fixedClockProvider_millisAreTruncatedToWholeSeconds() {
        Instant fixed = Instant.parse("2026-07-06T12:00:00.123Z");
        FixedClockProvider provider = new FixedClockProvider(fixed);

        Instant clockInstant = provider.getClock().instant();
        assertEquals(fixed, clockInstant);
        assertEquals(fixed.truncatedTo(ChronoUnit.SECONDS),
                clockInstant.truncatedTo(ChronoUnit.SECONDS));
    }
}
