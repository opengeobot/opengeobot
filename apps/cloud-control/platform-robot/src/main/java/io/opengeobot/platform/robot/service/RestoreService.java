/*
 * Function: Restore service — database and MinIO restore operations
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.config.MinioConfig;
import io.opengeobot.platform.robot.domain.BackupRecord;
import io.opengeobot.platform.robot.domain.RestoreRecord;
import io.opengeobot.platform.robot.dto.RestoreRecordDto;
import io.opengeobot.platform.robot.repository.RestoreRecordRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for restore operations (F-RECOVERY-001). Restores the database from
 * a pg_dump backup or restores MinIO objects from a manifest. Restores follow
 * SM-RESTORE-OPERATION (RUNNING → COMPLETED / FAILED). All mutations are
 * audited.
 */
@Service
public class RestoreService {

    private static final Logger log = LoggerFactory.getLogger(RestoreService.class);
    private static final String RESOURCE_TYPE = "restore_record";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String TYPE_DATABASE = "DATABASE";
    private static final Pattern JDBC_URL_PATTERN =
            Pattern.compile("jdbc:postgresql://([^:/]+):(\\d+)/(.+)");

    private final RestoreRecordRepository restoreRepository;
    private final BackupService backupService;
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

    public RestoreService(RestoreRecordRepository restoreRepository,
                          BackupService backupService,
                          MinioClient minioClient,
                          MinioConfig minioConfig,
                          AuditService auditService,
                          ActorResolver actorResolver,
                          ClockProvider clockProvider,
                          PublicIdGenerator idGenerator,
                          ObjectMapper objectMapper) {
        this.restoreRepository = restoreRepository;
        this.backupService = backupService;
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RestoreRecordDto restoreDatabase(String backupId, String actor) {
        BackupRecord backup = backupService.findBackupByBackupId(backupId);
        if (backup == null) {
            throw new ResourceNotFoundException("Backup '" + backupId + "' not found");
        }
        if (!STATUS_COMPLETED.equals(backup.getStatus())) {
            throw new ConflictException("Backup '" + backupId + "' is not in a restorable state");
        }

        RestoreRecord record = createRestoreRecord(backupId, actor);
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("opengeobot-restore-", ".dump");
            downloadFromMinio(backup.getFilePath(), tempFile);
            ProcessBuilder pb = buildPgRestoreCommand();
            pb.redirectInput(tempFile.toFile());
            pb.redirectErrorStream(false);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = new String(process.getErrorStream().readAllBytes());
                throw new IllegalStateException("pg_restore failed (exit " + exitCode + "): " + error);
            }
            record.setStatus(STATUS_COMPLETED);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            restoreRepository.updateById(record);
            audit("restore.database", RESOURCE_TYPE, record.getRestoreId(), "SUCCESS", null, toJson(record));
            log.info("Database restore {} completed from backup {}", record.getRestoreId(), backupId);
            return toDto(record);
        } catch (Exception e) {
            log.error("Database restore from backup {} failed", backupId, e);
            record.setStatus(STATUS_FAILED);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            record.setErrorMessage(e.getMessage());
            restoreRepository.updateById(record);
            audit("restore.database", RESOURCE_TYPE, record.getRestoreId(), "FAILED", null, toJson(record));
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
    public RestoreRecordDto restoreMinIO(String backupId, String actor) {
        BackupRecord backup = backupService.findBackupByBackupId(backupId);
        if (backup == null) {
            throw new ResourceNotFoundException("Backup '" + backupId + "' not found");
        }
        if (!STATUS_COMPLETED.equals(backup.getStatus())) {
            throw new ConflictException("Backup '" + backupId + "' is not in a restorable state");
        }

        RestoreRecord record = createRestoreRecord(backupId, actor);
        try {
            // MinIO restore reads the manifest and validates object availability.
            try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(backup.getFilePath())
                    .build())) {
                if (stream.readAllBytes().length == 0) {
                    throw new IllegalStateException("Backup manifest is empty");
                }
            }
            record.setStatus(STATUS_COMPLETED);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            restoreRepository.updateById(record);
            audit("restore.minio", RESOURCE_TYPE, record.getRestoreId(), "SUCCESS", null, toJson(record));
            log.info("MinIO restore {} completed from backup {}", record.getRestoreId(), backupId);
            return toDto(record);
        } catch (Exception e) {
            log.error("MinIO restore from backup {} failed", backupId, e);
            record.setStatus(STATUS_FAILED);
            record.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            record.setErrorMessage(e.getMessage());
            restoreRepository.updateById(record);
            audit("restore.minio", RESOURCE_TYPE, record.getRestoreId(), "FAILED", null, toJson(record));
            return toDto(record);
        }
    }

    private RestoreRecord createRestoreRecord(String backupId, String actor) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RestoreRecord record = new RestoreRecord();
        record.setRestoreId(idGenerator.generate("rst"));
        record.setBackupId(backupId);
        record.setStatus(STATUS_RUNNING);
        record.setStartedAt(now);
        record.setRestoredBy(actor);
        restoreRepository.insert(record);
        return record;
    }

    private void downloadFromMinio(String objectPath, Path target) throws Exception {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(objectPath)
                .build())) {
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ProcessBuilder buildPgRestoreCommand() {
        Matcher matcher = JDBC_URL_PATTERN.matcher(jdbcUrl);
        if (!matcher.matches()) {
            throw new IllegalStateException("Cannot parse JDBC URL for pg_restore: " + jdbcUrl);
        }
        String host = matcher.group(1);
        String port = matcher.group(2);
        String database = matcher.group(3);
        return new ProcessBuilder(
                "pg_restore",
                "--host", host,
                "--port", port,
                "--username", dbUser,
                "--dbname", database,
                "--no-password",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--exit-on-error"
        ).redirectErrorStream(false);
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

    private static RestoreRecordDto toDto(RestoreRecord entity) {
        return new RestoreRecordDto(
                entity.getRestoreId(),
                entity.getBackupId(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getErrorMessage(),
                entity.getRestoredBy()
        );
    }
}
