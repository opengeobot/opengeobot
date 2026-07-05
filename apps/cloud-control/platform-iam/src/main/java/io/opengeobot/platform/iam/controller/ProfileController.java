/*
 * Function: Profile controller — read and update the authenticated user's profile
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.controller;

import io.opengeobot.platform.iam.dto.UpdateProfileRequest;
import io.opengeobot.platform.iam.dto.UserProfileResponse;
import io.opengeobot.platform.iam.security.SecurityUser;
import io.opengeobot.platform.iam.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the authenticated user's own profile. Read access
 * requires the {@code platform.profile.read} permission; updates require
 * {@code platform.profile.manage}.
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('platform.profile.read')")
    public UserProfileResponse getProfile(@AuthenticationPrincipal SecurityUser securityUser) {
        return profileService.getCurrentProfile(securityUser.getUserId());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('platform.profile.manage')")
    public UserProfileResponse updateProfile(@AuthenticationPrincipal SecurityUser securityUser,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(securityUser.getUserId(), request);
    }
}
