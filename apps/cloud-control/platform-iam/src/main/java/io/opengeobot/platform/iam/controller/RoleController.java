/*
 * Function: Role controller — role management and permission assignment endpoints
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.iam.dto.AssignPermissionsRequest;
import io.opengeobot.platform.iam.dto.CreateRoleRequest;
import io.opengeobot.platform.iam.dto.PermissionDto;
import io.opengeobot.platform.iam.dto.RoleDto;
import io.opengeobot.platform.iam.dto.UpdateRoleRequest;
import io.opengeobot.platform.iam.security.SecurityUser;
import io.opengeobot.platform.iam.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for role management. Read operations require
 * {@code platform.role.read}; mutations require {@code platform.role.manage}.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('platform.role.read')")
    public PageResult<RoleDto> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        PageRequest pageRequest = PageRequest.of(page, pageSize);
        return roleService.list(pageRequest);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('platform.role.manage')")
    public ResponseEntity<RoleDto> create(@Valid @RequestBody CreateRoleRequest request,
                                           @AuthenticationPrincipal SecurityUser securityUser) {
        RoleDto created = roleService.create(request, securityUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('platform.role.read')")
    public RoleDto get(@PathVariable String roleId) {
        return roleService.getByRoleId(roleId);
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('platform.role.manage')")
    public RoleDto update(@PathVariable String roleId,
                          @Valid @RequestBody UpdateRoleRequest request,
                          @AuthenticationPrincipal SecurityUser securityUser) {
        return roleService.update(roleId, request, securityUser.getUserId());
    }

    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('platform.role.read')")
    public List<PermissionDto> getRolePermissions(@PathVariable String roleId) {
        return roleService.getRolePermissions(roleId);
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('platform.role.manage')")
    public void assignPermissions(@PathVariable String roleId,
                                  @Valid @RequestBody AssignPermissionsRequest request,
                                  @AuthenticationPrincipal SecurityUser securityUser) {
        roleService.assignPermissions(roleId, request.permissionCodes(), securityUser.getUserId());
    }
}
