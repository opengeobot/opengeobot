/*
 * Function: Mission state machine enum — SM-MISSION-001
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.common.mission;

import java.util.EnumSet;
import java.util.Set;

/**
 * Canonical mission lifecycle states for the SM-MISSION-001 state machine.
 * This is a code contract governed by the platform, not editable dictionary data.
 * Transitions: PENDING -> PLANNING -> READY -> EXECUTING -> COMPLETED/FAILED;
 * EXECUTING -> PAUSED -> EXECUTING (resume); any non-terminal -> CANCELLED.
 */
public enum MissionState {

    PENDING,
    PLANNING,
    READY,
    EXECUTING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<MissionState> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    /**
     * Returns whether the state is terminal (no further transitions allowed).
     */
    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /**
     * Validates that transitioning from this state to the target is allowed by
     * the SM-MISSION-001 state machine.
     *
     * @param target the desired next state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(MissionState target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> target == PLANNING || target == READY || target == CANCELLED;
            case PLANNING -> target == READY || target == CANCELLED;
            case READY -> target == EXECUTING || target == PLANNING || target == CANCELLED;
            case EXECUTING -> target == PAUSED || target == COMPLETED || target == FAILED || target == CANCELLED;
            case PAUSED -> target == EXECUTING || target == CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}
