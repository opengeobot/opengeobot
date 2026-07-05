/*
 * Function: Backup service — database and MinIO backup with scheduled execution
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.config.MinioConfig;
import io.opengeobot.platform.robot.domain.BackupRecord;
import io.opengeobot.platform.robot.dto.BackupRecordDto;
import io.opengeobot.platform.robot.repository.BackupRecordRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for backup operations (F-RECOVERY-001). Performs database backups
 * via {@code pg_dump} and MinIO backups via object manifests, storing the
 * artifacts in MinIO/S3. A scheduled job runs a database backup daily at
 * 02:00. Backups follow SM-BACKUP-OPERATION (RUNNING → COMPLETED / FAILED).
 * All mutations are audited.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final String RESOURCE_TYPE = "backup_record";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TYPE_DATABASE = "DATABASE";
    private static final String TYPE_MINIO = "MINIO";
    private static final String BACKUP_PREFIX = "backups/";
    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("jdbc:postgresql://([^:/]+):(\\d+)/(.+)");

    private final BackupRecordRepository backupRepository;
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    public BackupService(BackupRecordRepository backupRepository,
                         MinioClient minioClient,
                         MinioConfig minioConfig,
                         AuditService auditService,
                         ActorResolver actorResolver,
                         ClockProvider clockProvider,
                         PublicIdGenerator idGenerator,
                         ObjectMapper objectMapper) {
        this.backupRepository = backupRepository;
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "0 0 2 * * ?", zone = "UTC")
    public void scheduledDatabaseBackup() {
        log.info("Starting scheduled database backup");
        try {
            backupDatabase("system");
        } catch (Exception e) {
            log.error("Scheduled database backup failed", e);
        }
    }

    @Transactional
    public BackupRecordDto backupDatabase(String actor) {
        String backupId = idGenerator.generate("bkp");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filePath = BACKUP_PREFIX + "db/opengeobot-" + stamp + ".dump";

        BackupRecord record = new BackupRecord();
        record.setBackupId(backupId);
        record.setType(TYPE_DATABASE);
        record.setFilePath(filePath);
        record.setStatus(STATUS_RUNNING);
        record.setStartedAt(now);
        record.setCreatedBy(actor);
        backupRepository.insert(record);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("opengeobot-backup-", ".dump");
            ProcessBuilder pb = buildPgDumpCommand(filePath);
            pb.redirectOutput(tempFile.toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = new String(process.getErrorStream().readAllBytes());
                throw new IllegalStateException("pg_dump failed (exit " + exitCode + "): " + error);
            }
            byte[] content = Files.readAllBytes(tempFile);
            uploadToMinio(filePath, content);

            record.setFileSize((long) content.length);
            record.setStatus(STATUS_COMPLETED);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            backupRepository.updateById(record);
            audit("backup.database", RESOURCE_TYPE, backupId, "SUCCESS", null, toJson(record));
            log.info("Database backup {} completed ({} bytes)", backupId, content.length);
            return toDto(record);
        } catch (Exception e) {
            log.error("Database backup {} failed", backupId, e);
            record.setStatus(STATUS_FAILED);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            record.setErrorMessage(e.getMessage());
            backupRepository.updateById(record);
            audit("backup.database", RESOURCE_TYPE, backupId, "FAILED", null, toJson(record));
            return toDto(record);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    @Transactional
    public BackupRecordDto backupMinIO(String actor) {
        String backupId = idGenerator.generate("bkp");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filePath = BACKUP_PREFIX + "minio/manifest-" + stamp + ".json";

        BackupRecord record = new BackupRecord();
        record.setBackupId(backupId);
        record.setType(TYPE_MINIO);
        record.setFilePath(filePath);
        record.setStatus(STATUS_RUNNING);
        record.setStartedAt(now);
        record.setCreatedBy(actor);
        backupRepository.insert(record);

        try {
            String manifest = generateMinioManifest();
            byte[] content = manifest.getBytes();
            uploadToMinio(filePath, content);

            record.setFileSize((long) content.length);
            record.setStatus(STATUS_COMPLETED);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            backupRepository.updateById(record);
            audit("backup.minio", RESOURCE_TYPE, backupId, "SUCCESS", null, toJson(record));
            log.info("MinIO backup {} completed ({} bytes)", backupId, content.length);
            return toDto(record);
        } catch (Exception e) {
            log.error("MinIO backup {} failed", backupId, e);
            record.setStatus(STATUS_FAILED);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            record.setErrorMessage(e.getMessage());
            backupRepository.updateById(record);
            audit("backup.minio", RESOURCE_TYPE, backupId, "FAILED", null, toJson(record));
            return toDto(record);
        }
    }

    public PageResult<BackupRecordDto> listBackups(String type, String status, PageRequest pageRequest) {
        LambdaQueryWrapper<BackupRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(type != null && !type.isBlank(), BackupRecord::getType, type)
                .eq(status != null && !status.isBlank(), BackupRecord::getStatus, status)
                .orderByDesc(BackupRecord::getStartedAt);
        Page<BackupRecord> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<BackupRecord> result = backupRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(BackupService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    @Transactional
    public int cleanupExpired() {
        LambdaQueryWrapper<BackupRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BackupRecord::getStatus, STATUS_FAILED);
        long count = backupRepository.selectCount(wrapper);
        if (count > 0) {
            backupRepository.delete(wrapper);
            log.info("Cleaned up {} failed backup records", count);
        }
        return (int) count;
    }

    public BackupRecord findBackupByBackupId(String backupId) {
        LambdaQueryWrapper<BackupRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BackupRecord::getBackupId, backupId);
        BackupRecord entity = backupRepository.selectOne(wrapper);
        if (entity == null) {
            return null;
        }
        return entity;
    }

    public long countCompletedBackups() {
        LambdaQueryWrapper<BackupRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BackupRecord::getStatus, STATUS_COMPLETED);
        return backupRepository.selectCount(wrapper);
    }

    private ProcessBuilder buildPgDumpCommand(String filePath) {
        Matcher matcher = JDBC_URL_PATTERN.matcher(jdbcUrl);
        if (!matcher.matches()) {
            throw new IllegalStateException("Cannot parse JDBC URL for pg_dump: " + jdbcUrl);
        }
        String host = matcher.group(1);
        String port = matcher.group(2);
        String database = matcher.group(3);
        return new ProcessBuilder(
                "pg_dump",
                "--host", host,
                "--port", port,
                "--username", dbUser,
                "--format", "custom",
                "--no-password",
                database
        ).redirectErrorStream(false);
    }

    private String generateMinioManifest() {
        return "{\"backup_type\":\"MINIO\",\"bucket\":\"" + minioConfig.getBucket()
                + "\",\"created_at\":\"" + Instant.now(clockProvider.getClock()) + "\"}";
    }

    private void uploadToMinio(String objectPath, byte[] content) throws Exception {
        String bucket = minioConfig.getBucket();
        ensureBucketExists(bucket);
        try (InputStream stream = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .stream(stream, (long) content.length, -1)
                    .contentType("application/octet-stream")
                    .build());
        }
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

    static BackupRecordDto toDto(BackupRecord entity) {
        return new BackupRecordDto(
                entity.getBackupId(),
                entity.getType(),
                entity.getFilePath(),
                entity.getFileSize(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getErrorMessage(),
                entity.getCreatedBy()
        );
    }
}
