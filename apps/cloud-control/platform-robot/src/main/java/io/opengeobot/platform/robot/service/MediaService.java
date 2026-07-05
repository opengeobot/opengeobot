/*
 * Function: Media service — upload, download, delete and list with filters for F-MEDIA-001
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
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.BucketExistsArgs;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.config.MinioConfig;
import io.opengeobot.platform.robot.domain.MediaObject;
import io.opengeobot.platform.robot.dto.MediaObjectDto;
import io.opengeobot.platform.robot.repository.MediaObjectRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Service for media object management (F-MEDIA-001). Media files are stored
 * in MinIO/S3; the database row holds metadata and the object path. Upload
 * stores the file in MinIO first, then records the metadata. Delete removes
 * both the object and the metadata row. All mutations are audited.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final String RESOURCE_TYPE = "media_object";
    private static final String DEFAULT_MEDIA_TYPE = "DOCUMENT";
    private static final String OBJECT_PREFIX = "media/";

    private final MediaObjectRepository mediaObjectRepository;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public MediaService(MediaObjectRepository mediaObjectRepository,
                        MinioClient minioClient,
                        MinioConfig minioConfig,
                        AuditService auditService,
                        ActorResolver actorResolver,
                        ClockProvider clockProvider,
                        PublicIdGenerator idGenerator,
                        ObjectMapper objectMapper) {
        this.mediaObjectRepository = mediaObjectRepository;
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MediaObjectDto upload(MultipartFile file, String mediaType, String robotId,
                                  String missionId, OffsetDateTime expiresAt) {
        String mediaId = idGenerator.generate("med");
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String effectiveMediaType = (mediaType != null && !mediaType.isBlank()) ? mediaType : DEFAULT_MEDIA_TYPE;
        String filePath = OBJECT_PREFIX + mediaId + extractExtension(file.getOriginalFilename());
        String bucket = minioConfig.getBucket();

        ensureBucketExists(bucket);
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload media {} to MinIO", mediaId, e);
            throw new IllegalStateException("Failed to upload media file", e);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actor = actorResolver.currentActor();
        MediaObject entity = new MediaObject();
        entity.setMediaId(mediaId);
        entity.setFileName(file.getOriginalFilename());
        entity.setFilePath(filePath);
        entity.setFileSize(file.getSize());
        entity.setContentType(contentType);
        entity.setMediaType(effectiveMediaType);
        entity.setRobotId(robotId);
        entity.setMissionId(missionId);
        entity.setUploadedBy(actor);
        entity.setUploadedAt(now);
        entity.setExpiresAt(expiresAt);
        mediaObjectRepository.insert(entity);
        audit("media.upload", RESOURCE_TYPE, mediaId, "SUCCESS", null, toJson(entity));
        log.info("Uploaded media {} ({} bytes, type {})", mediaId, file.getSize(), effectiveMediaType);
        return toDto(entity);
    }

    public InputStream download(String mediaId) {
        MediaObject entity = findByMediaId(mediaId);
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(entity.getFilePath())
                    .build());
        } catch (Exception e) {
            log.error("Failed to download media {} from MinIO", mediaId, e);
            throw new IllegalStateException("Failed to download media file", e);
        }
    }

    public MediaObjectDto getMedia(String mediaId) {
        return toDto(findByMediaId(mediaId));
    }

    public PageResult<MediaObjectDto> listMedia(String robotId, String missionId, String mediaType,
                                                 PageRequest pageRequest) {
        LambdaQueryWrapper<MediaObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(robotId != null && !robotId.isBlank(), MediaObject::getRobotId, robotId)
                .eq(missionId != null && !missionId.isBlank(), MediaObject::getMissionId, missionId)
                .eq(mediaType != null && !mediaType.isBlank(), MediaObject::getMediaType, mediaType)
                .orderByDesc(MediaObject::getUploadedAt);
        Page<MediaObject> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<MediaObject> result = mediaObjectRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(MediaService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    @Transactional
    public void delete(String mediaId) {
        MediaObject entity = findByMediaId(mediaId);
        String payloadBefore = toJson(entity);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(entity.getFilePath())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to remove media {} from MinIO (continuing with metadata deletion)", mediaId, e);
        }
        mediaObjectRepository.deleteById(entity.getId());
        audit("media.delete", RESOURCE_TYPE, mediaId, "SUCCESS", payloadBefore, null);
        log.info("Deleted media {}", mediaId);
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

    private MediaObject findByMediaId(String mediaId) {
        LambdaQueryWrapper<MediaObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MediaObject::getMediaId, mediaId);
        MediaObject entity = mediaObjectRepository.selectOne(wrapper);
        if (entity == null) {
            throw new ResourceNotFoundException("Media '" + mediaId + "' not found");
        }
        return entity;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
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

    private static MediaObjectDto toDto(MediaObject entity) {
        return new MediaObjectDto(
                entity.getMediaId(),
                entity.getFileName(),
                entity.getFilePath(),
                entity.getFileSize(),
                entity.getContentType(),
                entity.getMediaType(),
                entity.getRobotId(),
                entity.getMissionId(),
                entity.getUploadedBy(),
                entity.getUploadedAt(),
                entity.getExpiresAt()
        );
    }
}
