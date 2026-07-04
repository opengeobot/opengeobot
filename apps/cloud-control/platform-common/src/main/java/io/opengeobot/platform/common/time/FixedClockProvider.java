/*
 * Function: Fixed clock provider — deterministic time for tests
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Test-only {@link ClockProvider} that always returns a fixed instant.
 * Not a Spring component — instantiate directly in tests.
 */
public final class FixedClockProvider implements ClockProvider {

    private final Clock clock;

    public FixedClockProvider(Instant fixedInstant) {
        this.clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
    }

    @Override
    public Clock getClock() {
        return clock;
    }
}
