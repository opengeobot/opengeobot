/*
 * Function: Permission controller — read-only permission catalog endpoints
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.controller;

import io.opengeobot.platform.iam.dto.PermissionDto;
import io.opengeobot.platform.iam.service.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the permission catalog. All endpoints require
 * {@code platform.permission.read}. Permission codes are read-only.
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('platform.permission.read')")
    public List<PermissionDto> list(@RequestParam(required = false) String module) {
        if (module != null && !module.isBlank()) {
            return permissionService.listByModule(module);
        }
        return permissionService.list();
    }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('platform.permission.read')")
    public List<PermissionDto> getPermissionsByRole(@PathVariable String roleId) {
        return permissionService.getPermissionsByRole(roleId);
    }
}
