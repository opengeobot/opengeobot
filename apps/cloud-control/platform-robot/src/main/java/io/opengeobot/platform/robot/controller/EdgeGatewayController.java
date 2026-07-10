/*
 * Function: Edge gateway REST controller — endpoints for F-EDGE-001
 * Time: 2026-07-10
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.ActivateEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.CreateEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.EdgeGatewayCertificateDto;
import io.opengeobot.platform.robot.dto.EdgeGatewayDto;
import io.opengeobot.platform.robot.dto.EdgeGatewayHeartbeatRequest;
import io.opengeobot.platform.robot.dto.RevokeEdgeGatewayRequest;
import io.opengeobot.platform.robot.dto.RotateCertificateRequest;
import io.opengeobot.platform.robot.service.EdgeGatewayService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller for edge gateway identity, activation, heartbeat and
 * certificate rotation under {@code /api/v1/edge-gateways}.
 */
@RestController
@RequestMapping("/api/v1/edge-gateways")
public class EdgeGatewayController {

    private final EdgeGatewayService edgeGatewayService;

    public EdgeGatewayController(EdgeGatewayService edgeGatewayService) {
        this.edgeGatewayService = edgeGatewayService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('edge.gateway.read')")
    public PageResponse<EdgeGatewayDto> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orgId) {
        PageResult<EdgeGatewayDto> result =
                edgeGatewayService.list(PageRequest.of(page, pageSize), status, orgId);
        return PageResponse.of(result);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('edge.gateway.manage')")
    public ResponseEntity<EdgeGatewayDto> register(@Valid @RequestBody CreateEdgeGatewayRequest request) {
        EdgeGatewayDto created = edgeGatewayService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/edge-gateways/" + created.gatewayId()))
                .body(created);
    }

    @GetMapping("/{gatewayId}")
    @PreAuthorize("hasAuthority('edge.gateway.read')")
    public EdgeGatewayDto get(@PathVariable String gatewayId) {
        return edgeGatewayService.getByGatewayId(gatewayId);
    }

    @PostMapping("/{gatewayId}/activate")
    @PreAuthorize("hasAuthority('edge.gateway.manage')")
    public EdgeGatewayDto activate(@PathVariable String gatewayId,
                                   @RequestBody(required = false) ActivateEdgeGatewayRequest request) {
        return edgeGatewayService.activate(gatewayId, request);
    }

    @PostMapping("/{gatewayId}/revoke")
    @PreAuthorize("hasAuthority('edge.gateway.manage')")
    public EdgeGatewayDto revoke(@PathVariable String gatewayId,
                                 @RequestBody(required = false) RevokeEdgeGatewayRequest request) {
        return edgeGatewayService.revoke(gatewayId, request);
    }

    @PostMapping("/{gatewayId}/heartbeat")
    @PreAuthorize("hasAuthority('edge.gateway.manage')")
    public EdgeGatewayDto heartbeat(@PathVariable String gatewayId,
                                    @RequestBody(required = false) EdgeGatewayHeartbeatRequest request) {
        return edgeGatewayService.heartbeat(gatewayId, request);
    }

    @PostMapping("/{gatewayId}/certificate-rotations")
    @PreAuthorize("hasAuthority('edge.gateway.certificate.rotate')")
    public EdgeGatewayCertificateDto rotateCertificate(
            @PathVariable String gatewayId,
            @Valid @RequestBody RotateCertificateRequest request) {
        return edgeGatewayService.rotateCertificate(gatewayId, request);
    }
}
