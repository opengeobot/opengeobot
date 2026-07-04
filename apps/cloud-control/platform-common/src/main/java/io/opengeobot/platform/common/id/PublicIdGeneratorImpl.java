/*
 * Function: ULID-based public ID generator implementation
 * Time: 2026-07-03
 * Author: AxeXie
 */
package io.opengeobot.platform.common.id;

import org.springframework.stereotype.Component;

/**
 * Default {@link PublicIdGenerator} producing {@code prefix + "_" + ULID} identifiers.
 */
@Component
public final class PublicIdGeneratorImpl implements PublicIdGenerator {

    @Override
    public String generate(String prefix) {
        return prefix + "_" + Ulid.next();
    }
}
