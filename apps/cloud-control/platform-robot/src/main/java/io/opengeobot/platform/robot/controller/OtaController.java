/*
 * Function: OTA REST controller — endpoints for package and campaign management
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.controller;

import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.CreateCampaignRequest;
import io.opengeobot.platform.robot.dto.DeploymentRecordDto;
import io.opengeobot.platform.robot.dto.FirmwarePackageDto;
import io.opengeobot.platform.robot.dto.ReleaseCampaignDto;
import io.opengeobot.platform.robot.service.OtaService;
import io.opengeobot.platform.robot.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for OTA publishing (F-OTA-001). Exposes endpoints under
 * {@code /api/v1/ota} per the OpenAPI contract. Campaigns follow SM-OTA-001
 * (CREATED → IN_PROGRESS → COMPLETED / ROLLED_BACK). Permissions:
 * {@code ops.ota.read} for GET, {@code ops.ota.manage} for POST.
 */
@RestController
@RequestMapping("/api/v1/ota")
public class OtaController {

    private final OtaService otaService;

    public OtaController(OtaService otaService) {
        this.otaService = otaService;
    }

    @GetMapping("/packages")
    public PageResponse<FirmwarePackageDto> listPackages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String type) {
        PageResult<FirmwarePackageDto> result =
                otaService.listPackages(type, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @PostMapping("/packages")
    public ResponseEntity<FirmwarePackageDto> uploadPackage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("version") String version,
            @RequestParam("type") String type,
            @RequestParam(value = "description", required = false) String description) {
        FirmwarePackageDto created = otaService.uploadPackage(file, name, version, type, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/packages/{packageId}")
    public FirmwarePackageDto getPackage(@PathVariable String packageId) {
        return otaService.getPackage(packageId);
    }

    @PostMapping("/campaigns")
    public ResponseEntity<ReleaseCampaignDto> createCampaign(@Valid @RequestBody CreateCampaignRequest request) {
        ReleaseCampaignDto created = otaService.createCampaign(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/campaigns")
    public PageResponse<ReleaseCampaignDto> listCampaigns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        PageResult<ReleaseCampaignDto> result =
                otaService.listCampaigns(status, PageRequest.of(page, pageSize));
        return PageResponse.of(result);
    }

    @GetMapping("/campaigns/{campaignId}")
    public OtaService.CampaignDetail getCampaign(@PathVariable String campaignId) {
        return otaService.getCampaign(campaignId);
    }

    @PostMapping("/campaigns/{campaignId}/rollback")
    public ReleaseCampaignDto rollback(@PathVariable String campaignId) {
        return otaService.rollback(campaignId);
    }

    @GetMapping("/campaigns/{campaignId}/deployments")
    public List<DeploymentRecordDto> getDeploymentStatus(@PathVariable String campaignId) {
        return otaService.getDeploymentStatus(campaignId);
    }
}
