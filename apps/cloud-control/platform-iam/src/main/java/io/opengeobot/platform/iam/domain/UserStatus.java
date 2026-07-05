/*
 * Function: User account status enum — canonical user lifecycle states
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.domain;

/**
 * Canonical user account status values. This is a code contract governed by
 * the platform, not editable dictionary data.
 */
public enum UserStatus {

    ACTIVE,
    DISABLED,
    LOCKED
}
