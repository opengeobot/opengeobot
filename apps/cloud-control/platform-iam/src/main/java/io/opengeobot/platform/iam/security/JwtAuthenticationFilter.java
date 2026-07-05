/*
 * Function: JWT authentication filter — extracts and validates Bearer tokens on each request
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.opengeobot.platform.iam.domain.User;
import io.opengeobot.platform.iam.service.PermissionCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts a Bearer token from the {@code Authorization} header, validates it
 * via {@link JwtTokenProvider}, and populates the {@link SecurityContextHolder}
 * with a {@link SecurityUser} backed by a lightweight {@link User} instance
 * carrying the user id and username from the JWT claims. Permission codes are
 * loaded dynamically via {@link PermissionCache} so that role changes take
 * effect immediately after cache invalidation, without waiting for the access
 * token to expire. If the token is missing or invalid the filter is a no-op
 * and the request continues unauthenticated.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final PermissionCache permissionCache;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, PermissionCache permissionCache) {
        this.tokenProvider = tokenProvider;
        this.permissionCache = permissionCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = tokenProvider.validateToken(token);
                String userId = claims.getSubject();
                String username = claims.get("name", String.class);

                User user = new User();
                user.setUserId(userId);
                user.setUsername(username);

                SecurityUser securityUser = new SecurityUser(user, permissionCache);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                log.debug("Invalid JWT token: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
