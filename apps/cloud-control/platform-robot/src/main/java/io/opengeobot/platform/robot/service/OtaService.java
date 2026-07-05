/*
 * Function: OTA service — package upload, campaign creation, deployment and rollback
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.BucketExistsArgs;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.config.MinioConfig;
import io.opengeobot.platform.robot.domain.DeploymentRecord;
import io.opengeobot.platform.robot.domain.FirmwarePackage;
import io.opengeobot.platform.robot.domain.ReleaseCampaign;
import io.opengeobot.platform.robot.dto.CreateCampaignRequest;
import io.opengeobot.platform.robot.dto.DeploymentRecordDto;
import io.opengeobot.platform.robot.dto.FirmwarePackageDto;
import io.opengeobot.platform.robot.dto.ReleaseCampaignDto;
import io.opengeobot.platform.robot.repository.DeploymentRecordRepository;
import io.opengeobot.platform.robot.repository.FirmwarePackageRepository;
import io.opengeobot.platform.robot.repository.ReleaseCampaignRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Service for OTA publishing (F-OTA-001). Manages firmware/skill package
 * upload to MinIO/S3, release campaign creation with canary-wave selection,
 * simulated deployment to target robots, and rollback. Campaigns follow
 * SM-OTA-001 (CREATED → IN_PROGRESS → COMPLETED / ROLLED_BACK / FAILED);
 * per-robot deployments follow SM-OTA-002
 * (PENDING → IN_PROGRESS → SUCCESS / FAILED / ROLLED_BACK). All mutations emit
 * domain events via the transactional outbox and are recorded in the audit
 * trail.
 */
@Service
public class OtaService {

    private static final Logger log = LoggerFactory.getLogger(OtaService.class);
    private static final String RESOURCE_TYPE = "firmware_package";
    private static final String CAMPAIGN_RESOURCE_TYPE = "release_campaign";
    private static final String DEPLOYMENT_RESOURCE_TYPE = "deployment_record";
    private static final String OBJECT_PREFIX = "ota/";
    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_ROLLED_BACK = "ROLLED_BACK";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String OTA_RELEASE_STARTED_EVENT = "ota.release_started.v1";
    private static final String OTA_COMPLETED_EVENT = "ota.completed.v1";
    private static final String OTA_ROLLBACK_EVENT = "ota.rolled_back.v1";

    private final FirmwarePackageRepository packageRepository;
    private final ReleaseCampaignRepository campaignRepository;
    private final DeploymentRecordRepository deploymentRepository;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final OutboxRepository outboxRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public OtaService(FirmwarePackageRepository packageRepository,
                     ReleaseCampaignRepository campaignRepository,
                     DeploymentRecordRepository deploymentRepository,
                     MinioClient minioClient,
                     MinioConfig minioConfig,
                     OutboxRepository outboxRepository,
                     AuditService auditService,
                     ActorResolver actorResolver,
                     ClockProvider clockProvider,
                     PublicIdGenerator idGenerator,
                     ObjectMapper objectMapper) {
        this.packageRepository = packageRepository;
        this.campaignRepository = campaignRepository;
        this.deploymentRepository = deploymentRepository;
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FirmwarePackageDto uploadPackage(MultipartFile file, String name, String version,
                                            String type, String description) {
        String packageId = idGenerator.generate("pkg");
        String filePath = OBJECT_PREFIX + packageId + extractExtension(file.getOriginalFilename());
        String bucket = minioConfig.getBucket();
        ensureBucketExists(bucket);

        byte[] content;
        try (InputStream inputStream = file.getInputStream()) {
            content = inputStream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to read package file for {}", packageId, e);
            throw new IllegalStateException("Failed to read package file", e);
        }

        try (InputStream uploadStream = new java.io.ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .stream(uploadStream, (long) content.length, -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload package {} to MinIO", packageId, e);
            throw new IllegalStateException("Failed to upload package file", e);
        }

        String checksum = sha256(content);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        FirmwarePackage entity = new FirmwarePackage();
        entity.setPackageId(packageId);
        entity.setName(name);
        entity.setVersion(version);
        entity.setType(type);
        entity.setFilePath(filePath);
        entity.setFileSize((long) content.length);
        entity.setChecksum(checksum);
        entity.setDescription(description);
        entity.setCreatedAt(now);
        entity.setCreatedBy(actor);
        packageRepository.insert(entity);
        audit("ota.package.upload", RESOURCE_TYPE, packageId, "SUCCESS", null, toJson(entity));
        log.info("Uploaded OTA package {} ({} bytes, checksum {})", packageId, content.length, checksum);
        return toDto(entity);
    }

    public PageResult<FirmwarePackageDto> listPackages(String type, PageRequest pageRequest) {
        LambdaQueryWrapper<FirmwarePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(type != null && !type.isBlank(), FirmwarePackage::getType, type)
                .orderByDesc(FirmwarePackage::getCreatedAt);
        Page<FirmwarePackage> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<FirmwarePackage> result = packageRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(OtaService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public FirmwarePackageDto getPackage(String packageId) {
        return toDto(findPackageByPackageId(packageId));
    }

    @Transactional
    public ReleaseCampaignDto createCampaign(CreateCampaignRequest request) {
        FirmwarePackage pkg = findPackageByPackageId(request.packageId());
        int canaryPercent = request.canaryPercent() != null ? request.canaryPercent() : 100;
        List<String> canaryRobots = selectCanaryRobots(request.targetRobots(), canaryPercent);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        ReleaseCampaign campaign = new ReleaseCampaign();
        campaign.setCampaignId(idGenerator.generate("ota"));
        campaign.setPackageId(pkg.getPackageId());
        campaign.setCanaryPercent(canaryPercent);
        campaign.setStatus(STATUS_CREATED);
        campaign.setTargetRobotIds(canaryRobots);
        campaign.setCreatedAt(now);
        campaign.setCreatedBy(actor);
        campaignRepository.insert(campaign);

        for (String robotId : canaryRobots) {
            DeploymentRecord record = new DeploymentRecord();
            record.setRecordId(idGenerator.generate("dep"));
            record.setCampaignId(campaign.getCampaignId());
            record.setRobotId(robotId);
            record.setStatus(STATUS_PENDING);
            deploymentRepository.insert(record);
        }

        writeReleaseStartedEvent(campaign, canaryRobots);
        audit("ota.campaign.create", CAMPAIGN_RESOURCE_TYPE, campaign.getCampaignId(),
                "SUCCESS", null, toJson(campaign));
        log.info("Created OTA campaign {} for package {} ({} canary robots)",
                campaign.getCampaignId(), pkg.getPackageId(), canaryRobots.size());
        return toDto(campaign);
    }

    public PageResult<ReleaseCampaignDto> listCampaigns(String status, PageRequest pageRequest) {
        LambdaQueryWrapper<ReleaseCampaign> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null && !status.isBlank(), ReleaseCampaign::getStatus, status)
                .orderByDesc(ReleaseCampaign::getCreatedAt);
        Page<ReleaseCampaign> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<ReleaseCampaign> result = campaignRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(OtaService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public CampaignDetail getCampaign(String campaignId) {
        ReleaseCampaign campaign = findCampaignByCampaignId(campaignId);
        List<DeploymentRecord> deployments = findDeploymentsByCampaign(campaignId);
        return new CampaignDetail(toDto(campaign),
                deployments.stream().map(OtaService::toDeploymentDto).toList());
    }

    @Transactional
    public ReleaseCampaignDto startDeployment(String campaignId) {
        ReleaseCampaign campaign = findCampaignByCampaignId(campaignId);
        if (!STATUS_CREATED.equals(campaign.getStatus())) {
            throw new ConflictException("Campaign '" + campaignId + "' is not in CREATED state");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        campaign.setStatus(STATUS_IN_PROGRESS);
        campaign.setStartedAt(now);
        campaignRepository.updateById(campaign);

        List<DeploymentRecord> deployments = findDeploymentsByCampaign(campaignId);
        int successCount = 0;
        int failedCount = 0;
        for (DeploymentRecord record : deployments) {
            record.setStatus(STATUS_IN_PROGRESS);
            record.setStartedAt(now);
            deploymentRepository.updateById(record);
            // Simulated OTA push — M3-M6 may replace this with real edge delivery.
            record.setStatus(STATUS_SUCCESS);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            deploymentRepository.updateById(record);
            successCount++;
        }

        campaign.setStatus(STATUS_COMPLETED);
        campaign.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        campaignRepository.updateById(campaign);
        writeCompletedEvent(campaign, successCount, failedCount);
        audit("ota.deployment.start", CAMPAIGN_RESOURCE_TYPE, campaignId, "SUCCESS",
                null, toJson(campaign));
        log.info("Started and completed OTA deployment for campaign {} ({} success, {} failed)",
                campaignId, successCount, failedCount);
        return toDto(campaign);
    }

    @Transactional
    public ReleaseCampaignDto rollback(String campaignId) {
        ReleaseCampaign campaign = findCampaignByCampaignId(campaignId);
        if (STATUS_ROLLED_BACK.equals(campaign.getStatus())) {
            throw new ConflictException("Campaign '" + campaignId + "' is already rolled back");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<DeploymentRecord> deployments = findDeploymentsByCampaign(campaignId);
        List<String> affectedRobots = new ArrayList<>();
        for (DeploymentRecord record : deployments) {
            if (!STATUS_PENDING.equals(record.getStatus())) {
                record.setStatus(STATUS_ROLLED_BACK);
                record.setCompletedAt(now);
                deploymentRepository.updateById(record);
                affectedRobots.add(record.getRobotId());
            }
        }
        campaign.setStatus(STATUS_ROLLED_BACK);
        campaign.setCompletedAt(now);
        campaignRepository.updateById(campaign);
        writeRollbackEvent(campaign, affectedRobots);
        audit("ota.campaign.rollback", CAMPAIGN_RESOURCE_TYPE, campaignId,
                "SUCCESS", null, toJson(campaign));
        log.info("Rolled back OTA campaign {} ({} robots affected)", campaignId, affectedRobots.size());
        return toDto(campaign);
    }

    public List<DeploymentRecordDto> getDeploymentStatus(String campaignId) {
        if (!existsCampaign(campaignId)) {
            throw new ResourceNotFoundException("Campaign '" + campaignId + "' not found");
        }
        return findDeploymentsByCampaign(campaignId).stream()
                .map(OtaService::toDeploymentDto).toList();
    }

    private List<String> selectCanaryRobots(List<String> targetRobots, int canaryPercent) {
        if (canaryPercent >= 100) {
            return new ArrayList<>(targetRobots);
        }
        int count = Math.max(1, (int) Math.ceil(targetRobots.size() * (canaryPercent / 100.0)));
        return new ArrayList<>(targetRobots.subList(0, Math.min(count, targetRobots.size())));
    }

    private FirmwarePackage findPackageByPackageId(String packageId) {
        LambdaQueryWrapper<FirmwarePackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FirmwarePackage::getPackageId, packageId);
        FirmwarePackage entity = packageRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Package '" + packageId + "' not found");
        }
        return entity;
    }

    private ReleaseCampaign findCampaignByCampaignId(String campaignId) {
        LambdaQueryWrapper<ReleaseCampaign> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReleaseCampaign::getCampaignId, campaignId);
        ReleaseCampaign entity = campaignRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Campaign '" + campaignId + "' not found");
        }
        return entity;
    }

    private boolean existsCampaign(String campaignId) {
        LambdaQueryWrapper<ReleaseCampaign> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReleaseCampaign::getCampaignId, campaignId);
        return campaignRepository.selectCount(wrapper) > 0;
    }

    private List<DeploymentRecord> findDeploymentsByCampaign(String campaignId) {
        LambdaQueryWrapper<DeploymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeploymentRecord::getCampaignId, campaignId)
                .orderByAsc(DeploymentRecord::getId);
        return deploymentRepository.selectList(wrapper);
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.warn("Failed to check or create MinIO bucket '{}'", bucket, e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 checksum", e);
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private void writeReleaseStartedEvent(ReleaseCampaign campaign, List<String> targetRobots) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "campaign_id", campaign.getCampaignId(),
                "package_id", campaign.getPackageId(),
                "target_robots", targetRobots,
                "canary_percent", campaign.getCanaryPercent(),
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", actorResolver.currentTraceId() != null ? actorResolver.currentTraceId() : ""
        );
        saveOutboxEvent(OTA_RELEASE_STARTED_EVENT, CAMPAIGN_RESOURCE_TYPE,
                campaign.getCampaignId(), payload);
    }

    private void writeCompletedEvent(ReleaseCampaign campaign, int successCount, int failedCount) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "campaign_id", campaign.getCampaignId(),
                "package_id", campaign.getPackageId(),
                "success_count", successCount,
                "failed_count", failedCount,
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", actorResolver.currentTraceId() != null ? actorResolver.currentTraceId() : ""
        );
        saveOutboxEvent(OTA_COMPLETED_EVENT, CAMPAIGN_RESOURCE_TYPE,
                campaign.getCampaignId(), payload);
    }

    private void writeRollbackEvent(ReleaseCampaign campaign, List<String> affectedRobots) {
        Map<String, Object> payload = Map.of(
                "event_id", idGenerator.generate("evt"),
                "campaign_id", campaign.getCampaignId(),
                "package_id", campaign.getPackageId(),
                "affected_robots", affectedRobots,
                "reason", "Manual rollback requested",
                "occurred_at", Instant.now(clockProvider.getClock()).toString(),
                "trace_id", actorResolver.currentTraceId() != null ? actorResolver.currentTraceId() : ""
        );
        saveOutboxEvent(OTA_ROLLBACK_EVENT, CAMPAIGN_RESOURCE_TYPE,
                campaign.getCampaignId(), payload);
    }

    private void saveOutboxEvent(String eventType, String aggregateType, String aggregateId,
                                 Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent(
                null,
                idGenerator.generate("evt"),
                eventType,
                "1",
                aggregateType,
                aggregateId,
                1L,
                toJson(payload),
                Instant.now(clockProvider.getClock()),
                actorResolver.currentTraceId(),
                false,
                null,
                0
        );
        outboxRepository.save(event);
    }

    private void audit(String action, String resourceType, String resourceId, String result,
                       String payloadBefore, String payloadAfter) {
        AuditEvent event = new AuditEvent(
                "user",
                actorResolver.currentActor(),
                action,
                resourceType,
                resourceId,
                result,
                null,
                null,
                null,
                actorResolver.currentTraceId(),
                null,
                Instant.now(clockProvider.getClock()),
                payloadBefore,
                payloadAfter
        );
        auditService.record(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise value to JSON for audit", e);
            return null;
        }
    }

    private static FirmwarePackageDto toDto(FirmwarePackage entity) {
        return new FirmwarePackageDto(
                entity.getPackageId(),
                entity.getName(),
                entity.getVersion(),
                entity.getType(),
                entity.getFilePath(),
                entity.getFileSize(),
                entity.getChecksum(),
                entity.getDescription(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    private static ReleaseCampaignDto toDto(ReleaseCampaign entity) {
        return new ReleaseCampaignDto(
                entity.getCampaignId(),
                entity.getPackageId(),
                entity.getCanaryPercent(),
                entity.getStatus(),
                entity.getTargetRobotIds(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    private static DeploymentRecordDto toDeploymentDto(DeploymentRecord entity) {
        return new DeploymentRecordDto(
                entity.getRecordId(),
                entity.getCampaignId(),
                entity.getRobotId(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getErrorMessage()
        );
    }

    /**
     * Composite response for the campaign detail endpoint, carrying the
     * campaign state and its deployment records.
     */
    public record CampaignDetail(ReleaseCampaignDto campaign, List<DeploymentRecordDto> deployments) {
    }
}
