/*
 * Function: In-memory cache for published dictionary items — invalidated on publish
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.dict.service;

import io.opengeobot.platform.governance.dict.domain.DictItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local cache of dictionary items keyed by {@code typeCode}. Consumers
 * read published snapshots from the cache; the cache is loaded lazily and
 * invalidated whenever a type is published or modified so stale entries are
 * never served. A multi-instance deployment relies on the publish event to
 * trigger invalidation on each node.
 */
@Component
public class DictCache {

    private final Map<String, List<DictItem>> cache = new ConcurrentHashMap<>();

    public List<DictItem> get(String typeCode) {
        return cache.get(typeCode);
    }

    public void put(String typeCode, List<DictItem> items) {
        cache.put(typeCode, List.copyOf(items));
    }

    public void invalidate(String typeCode) {
        cache.remove(typeCode);
    }

    public void invalidateAll() {
        cache.clear();
    }
}
