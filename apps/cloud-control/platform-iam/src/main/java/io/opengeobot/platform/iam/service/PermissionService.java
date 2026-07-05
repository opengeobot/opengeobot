/*
 * Function: Permission service — read-only permission catalog queries
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

import io.opengeobot.platform.iam.domain.Permission;
import io.opengeobot.platform.iam.dto.PermissionDto;
import io.opengeobot.platform.iam.repository.PermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only application service for the permission catalog. Permission codes are
 * stable contracts; this service never mutates them.
 */
@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> list() {
        return permissionRepository.findAll().stream()
                .map(PermissionService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> listByModule(String module) {
        return permissionRepository.findByModule(module).stream()
                .map(PermissionService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> getPermissionsByRole(String roleId) {
        return permissionRepository.findByRoleId(roleId).stream()
                .map(PermissionService::toDto)
                .toList();
    }

    private static PermissionDto toDto(Permission permission) {
        return new PermissionDto(
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getModule(),
                permission.getDescription(),
                permission.getResourceType(),
                permission.getAction()
        );
    }
}
