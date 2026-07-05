/*
 * Function: In-memory permission cache — caches user permission codes for fast authorization
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

import io.opengeobot.platform.iam.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of user permission codes backed by a {@link ConcurrentHashMap}.
 * On a cache miss the permissions are loaded from the database via
 * {@link UserRepository#findPermissionCodesByUserId(String)} and cached for
 * subsequent lookups. The cache must be invalidated whenever a user's roles
 * change or a role the user holds has its permissions replaced.
 */
@Component
public class PermissionCache {

    private static final Logger log = LoggerFactory.getLogger(PermissionCache.class);

    private final ConcurrentHashMap<String, Set<String>> cache = new ConcurrentHashMap<>();
    private final UserRepository userRepository;

    public PermissionCache(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Return the set of permission codes for the given user. Loads from the
     * database on cache miss.
     *
     * @param userId stable public user identifier
     * @return unmodifiable set of permission codes
     */
    public Set<String> getPermissions(String userId) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptySet();
        }
        return cache.computeIfAbsent(userId, this::loadPermissions);
    }

    /**
     * Invalidate the cached permissions for a single user. Called when the
     * user's roles are reassigned.
     *
     * @param userId stable public user identifier
     */
    public void invalidate(String userId) {
        if (userId != null) {
            cache.remove(userId);
            log.debug("Permission cache invalidated for userId={}", userId);
        }
    }

    /**
     * Clear all cached permissions. Called when a role's permissions are
     * replaced, affecting all users holding that role.
     */
    public void invalidateAll() {
        cache.clear();
        log.debug("Permission cache fully invalidated");
    }

    private Set<String> loadPermissions(String userId) {
        List<String> codes = userRepository.findPermissionCodesByUserId(userId);
        Set<String> result = codes != null ? Set.copyOf(codes) : Collections.emptySet();
        log.debug("Loaded {} permission codes for userId={}", result.size(), userId);
        return result;
    }
}
