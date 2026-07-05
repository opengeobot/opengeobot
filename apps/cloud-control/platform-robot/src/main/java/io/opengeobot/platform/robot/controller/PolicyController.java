/*
 * Function: Policy REST controller — endpoints for F-POLICY-001 policy management
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreatePolicyRequest;
import io.opengeobot.platform.robot.dto.PolicyDto;
import io.opengeobot.platform.robot.dto.PolicyVersionDto;
import io.opengeobot.platform.robot.dto.UpdatePolicyRequest;
import io.opengeobot.platform.robot.service.PolicyService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for policy management. Exposes endpoints under
 * {@code /api/v1/policies} per the OpenAPI contract. Policies follow the
 * SM-POLICY-001 state machine (DRAFT → PUBLISHED → ARCHIVED). Permissions:
 * {@code policy.policy.read} for GET, {@code policy.policy.manage} for
 * POST/PUT, {@code policy.policy.publish} for publish.
 */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public PageResponse<PolicyDto> listPolicies(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        PageResult<PolicyDto> result = policyService.list(status, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping
    public ResponseEntity<PolicyDto> createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        PolicyDto created = policyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{policyId}")
    public PolicyDto getPolicy(@PathVariable String policyId) {
        return policyService.get(policyId);
    }

    @PutMapping("/{policyId}")
    public PolicyDto updatePolicy(@PathVariable String policyId,
                                  @Valid @RequestBody UpdatePolicyRequest request) {
        return policyService.update(policyId, request);
    }

    @PostMapping("/{policyId}/publish")
    public PolicyDto publishPolicy(@PathVariable String policyId) {
        return policyService.publish(policyId);
    }

    @GetMapping("/{policyId}/versions")
    public PageResponse<PolicyVersionDto> listVersions(
            @PathVariable String policyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<PolicyVersionDto> result = policyService.listVersions(policyId, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }
}
