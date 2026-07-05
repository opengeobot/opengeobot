/*
 * Function: User controller — user management endpoints with permission checks
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.iam.dto.AssignRolesRequest;
import io.opengeobot.platform.iam.dto.CreateUserRequest;
import io.opengeobot.platform.iam.dto.RoleDto;
import io.opengeobot.platform.iam.dto.UpdateProfileRequest;
import io.opengeobot.platform.iam.dto.UpdateUserStatusRequest;
import io.opengeobot.platform.iam.dto.UserDto;
import io.opengeobot.platform.iam.security.SecurityUser;
import io.opengeobot.platform.iam.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for user management. Read operations require
 * {@code platform.user.read}; mutations require {@code platform.user.manage}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('platform.user.read')")
    public PageResult<UserDto> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String orgId,
                                    @RequestParam(required = false) String status) {
        PageRequest pageRequest = PageRequest.of(page, pageSize);
        return userService.list(pageRequest, keyword, orgId, status);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('platform.user.manage')")
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request,
                                          @AuthenticationPrincipal SecurityUser securityUser) {
        UserDto created = userService.create(request, securityUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('platform.user.read')")
    public UserDto get(@PathVariable String userId) {
        return userService.getByUserId(userId);
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('platform.user.manage')")
    public UserDto update(@PathVariable String userId,
                          @Valid @RequestBody UpdateProfileRequest request,
                          @AuthenticationPrincipal SecurityUser securityUser) {
        return userService.update(userId, request, securityUser.getUserId());
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('platform.user.manage')")
    public void updateStatus(@PathVariable String userId,
                             @Valid @RequestBody UpdateUserStatusRequest request,
                             @AuthenticationPrincipal SecurityUser securityUser) {
        userService.updateStatus(userId, request.status(), request.reason(), securityUser.getUserId());
    }

    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('platform.user.read')")
    public List<RoleDto> getUserRoles(@PathVariable String userId) {
        return userService.getUserRoles(userId);
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('platform.user.manage')")
    public void assignRoles(@PathVariable String userId,
                            @Valid @RequestBody AssignRolesRequest request,
                            @AuthenticationPrincipal SecurityUser securityUser) {
        userService.assignRoles(userId, request.roleIds(), securityUser.getUserId());
    }
}
