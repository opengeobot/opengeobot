/*
 * Function: Public ID generator interface — prefixed, sortable external identifiers
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.id;

/**
 * Generates human-readable, prefixed public identifiers backed by ULIDs.
 * Example: {@code generate("mission") -> "mission_01HXYZ..."}.
 */
public interface PublicIdGenerator {

    String generate(String prefix);
}
