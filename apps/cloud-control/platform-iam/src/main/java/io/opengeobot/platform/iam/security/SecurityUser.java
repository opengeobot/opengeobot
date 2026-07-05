/*
 * Function: Spring Security UserDetails adapter wrapping the User entity and permissions
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.security;

import io.opengeobot.platform.iam.domain.User;
import io.opengeobot.platform.iam.service.PermissionCache;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Adapts the {@link User} domain entity and its granted permission codes to the
 * Spring Security {@link UserDetails} contract. Permission codes are exposed as
 * {@link SimpleGrantedAuthority} instances so that method-level security
 * ({@code @PreAuthorize}) and {@code hasAuthority()} checks work against the
 * stable permission code contract.
 * <p>
 * When a {@link PermissionCache} is supplied, permissions are loaded from the
 * cache (falling back to the database on cache miss) so that role changes take
 * effect immediately after cache invalidation. When no cache is supplied, the
 * permissions passed at construction time are used.
 */
public class SecurityUser implements UserDetails {

    private final User user;
    private final List<String> permissions;
    private final PermissionCache permissionCache;

    public SecurityUser(User user, List<String> permissions) {
        this.user = user;
        this.permissions = permissions != null ? permissions : List.of();
        this.permissionCache = null;
    }

    public SecurityUser(User user, PermissionCache permissionCache) {
        this.user = user;
        this.permissions = List.of();
        this.permissionCache = permissionCache;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return getEffectivePermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    public String getUserId() {
        return user.getUserId();
    }

    public User getUser() {
        return user;
    }

    public List<String> getPermissions() {
        return getEffectivePermissions();
    }

    private List<String> getEffectivePermissions() {
        if (permissionCache != null) {
            Set<String> cached = permissionCache.getPermissions(user.getUserId());
            return List.copyOf(cached);
        }
        return permissions;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
