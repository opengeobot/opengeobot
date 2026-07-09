/*
 * Function: Profile service — read and update the authenticated user's profile
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.error.ErrorCode;
import io.opengeobot.platform.common.error.PlatformException;
import io.opengeobot.platform.common.id.Ulid;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.iam.domain.User;
import io.opengeobot.platform.iam.dto.UpdateProfileRequest;
import io.opengeobot.platform.iam.dto.UserProfileResponse;
import io.opengeobot.platform.iam.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Application service for the authenticated user's own profile. Supports
 * reading the current profile and updating mutable fields (display name, email,
 * phone, avatar). Identity fields (id, username) are not editable. Each update
 * writes an audit event linked by the trace_id in the MDC.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ClockProvider clockProvider;

    public ProfileService(UserRepository userRepository,
                          AuditService auditService,
                          ClockProvider clockProvider) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.clockProvider = clockProvider;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentProfile(String userId) {
        User user = findUserOrThrow(userId);
        List<String> permissions = userRepository.findPermissionCodesByUserId(userId);
        return toResponse(user, permissions);
    }

    @Transactional
    public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);

        String displayName = request.displayName();
        String email = request.email();
        String phone = request.phone();
        String avatar = request.avatar();

        boolean changed = false;
        if (displayName != null) {
            user.setDisplayName(displayName);
            changed = true;
        }
        if (email != null) {
            user.setEmail(email);
            changed = true;
        }
        if (phone != null) {
            user.setPhone(phone);
            changed = true;
        }
        if (avatar != null) {
            user.setAvatar(avatar);
            changed = true;
        }

        if (changed) {
            OffsetDateTime now = OffsetDateTime.now(clockProvider.getClock());
            user.setUpdatedAt(now);
            user.setUpdatedBy(userId);
            userRepository.updateById(user);

            String traceId = MDC.get("traceId");
            if (traceId == null || traceId.isBlank()) {
                traceId = Ulid.next();
                MDC.put("traceId", traceId);
            }

            auditService.record(new AuditEvent(
                    "user", userId, "UPDATE_PROFILE", "user", userId,
                    "SUCCESS", null, null, null, traceId, null,
                    Instant.now(clockProvider.getClock()), null, null
            ));

            log.info("Profile updated: userId={}", userId);
        }

        return toResponse(user, userRepository.findPermissionCodesByUserId(userId));
    }

    private User findUserOrThrow(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new PlatformException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return user;
    }

    private static UserProfileResponse toResponse(User user, List<String> permissions) {
        return new UserProfileResponse(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getStatus(),
                permissions
        );
    }
}
