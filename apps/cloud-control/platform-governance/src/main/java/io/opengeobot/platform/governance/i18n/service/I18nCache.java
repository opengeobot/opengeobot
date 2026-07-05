/*
 * Function: In-memory cache for i18n resources — invalidated on change
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.i18n.service;

import io.opengeobot.platform.governance.i18n.domain.I18nResource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local cache of i18n translations keyed by {@code resourceKey + locale}.
 * Resolved translations are read from the cache on the hot path; entries are
 * invalidated whenever a resource is created, updated or deleted so stale
 * translations are never served.
 */
@Component
public class I18nCache {

    private final Map<String, I18nResource> cache = new ConcurrentHashMap<>();

    public I18nResource get(String resourceKey, String locale) {
        return cache.get(cacheKey(resourceKey, locale));
    }

    public void put(I18nResource resource) {
        cache.put(cacheKey(resource.getResourceKey(), resource.getLocale()), resource);
    }

    public void invalidate(String resourceKey, String locale) {
        cache.remove(cacheKey(resourceKey, locale));
    }

    public void invalidateAll() {
        cache.clear();
    }

    private String cacheKey(String resourceKey, String locale) {
        return resourceKey + "#" + locale;
    }
}
