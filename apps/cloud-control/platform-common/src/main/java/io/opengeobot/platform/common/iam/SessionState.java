/*
 * Function: Session state machine enum — SM-IAM-001
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.common.iam;

/**
 * Canonical session states for the SM-IAM-001 state machine.
 * This is a code contract governed by the platform, not editable dictionary data.
 */
public enum SessionState {

    ACTIVE,
    REFRESHING,
    EXPIRED,
    REVOKED
}
