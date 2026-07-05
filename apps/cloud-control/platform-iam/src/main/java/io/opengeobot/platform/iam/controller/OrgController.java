/*
 * Function: Organization controller — org tree management endpoints
 * Time: 2026-07-04
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.controller;

import io.opengeobot.platform.iam.dto.CreateOrgRequest;
import io.opengeobot.platform.iam.dto.OrgDto;
import io.opengeobot.platform.iam.dto.OrgTreeNodeDto;
import io.opengeobot.platform.iam.dto.UpdateOrgRequest;
import io.opengeobot.platform.iam.dto.UserDto;
import io.opengeobot.platform.iam.security.SecurityUser;
import io.opengeobot.platform.iam.service.OrgService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for organization management. List is available to all
 * authenticated users; mutations require {@code platform.org.manage}.
 */
@RestController
@RequestMapping("/api/v1/orgs")
public class OrgController {

    private final OrgService orgService;

    public OrgController(OrgService orgService) {
        this.orgService = orgService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<OrgTreeNodeDto> list() {
        return orgService.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('platform.org.manage')")
    public ResponseEntity<OrgDto> create(@Valid @RequestBody CreateOrgRequest request,
                                        @AuthenticationPrincipal SecurityUser securityUser) {
        OrgDto created = orgService.create(request, securityUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{orgId}")
    @PreAuthorize("isAuthenticated()")
    public OrgDto get(@PathVariable String orgId) {
        return orgService.getByOrgId(orgId);
    }

    @PutMapping("/{orgId}")
    @PreAuthorize("hasAuthority('platform.org.manage')")
    public OrgDto update(@PathVariable String orgId,
                         @Valid @RequestBody UpdateOrgRequest request,
                         @AuthenticationPrincipal SecurityUser securityUser) {
        return orgService.update(orgId, request, securityUser.getUserId());
    }

    @DeleteMapping("/{orgId}")
    @PreAuthorize("hasAuthority('platform.org.manage')")
    public void delete(@PathVariable String orgId,
                       @AuthenticationPrincipal SecurityUser securityUser) {
        orgService.delete(orgId, securityUser.getUserId());
    }

    @GetMapping("/{orgId}/users")
    @PreAuthorize("hasAuthority('platform.user.read')")
    public List<UserDto> getUsersByOrg(@PathVariable String orgId) {
        return orgService.getUsersByOrg(orgId);
    }
}
