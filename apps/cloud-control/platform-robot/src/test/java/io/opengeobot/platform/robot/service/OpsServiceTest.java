/*
 * Function: OpsService unit tests — health checks for NATS and MinIO
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.NatsConnectionManager;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.config.MinioConfig;
import io.opengeobot.platform.robot.domain.HealthCheck;
import io.opengeobot.platform.robot.dto.HealthCheckDto;
import io.opengeobot.platform.robot.repository.AlarmEventRepository;
import io.opengeobot.platform.robot.repository.HealthCheckRepository;
import io.opengeobot.platform.robot.repository.MetricSnapshotRepository;
import io.opengeobot.platform.robot.repository.MissionRepository;
import io.opengeobot.platform.robot.repository.ReportRecordRepository;
import io.opengeobot.platform.robot.repository.RobotRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link OpsService} health checks. Verifies that NATS
 * and MinIO probes reflect real connectivity status rather than always
 * returning HEALTHY.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpsServiceTest {

    @Mock private RobotRepository robotRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private AlarmEventRepository alarmEventRepository;
    @Mock private MetricSnapshotRepository metricSnapshotRepository;
    @Mock private HealthCheckRepository healthCheckRepository;
    @Mock private ReportRecordRepository reportRecordRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private ObjectProvider<NatsConnectionManager> natsProvider;
    @Mock private MinioClient minioClient;
    @Mock private MinioConfig minioConfig;
    @Mock private NatsConnectionManager natsManager;

    private OpsService opsService;

    @BeforeEach
    void setUp() {
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(actorResolver.currentActor()).thenReturn("test-user");
        when(actorResolver.currentTraceId()).thenReturn("trace-001");
        when(minioConfig.getBucket()).thenReturn("test-bucket");
        when(healthCheckRepository.insert(any(HealthCheck.class))).thenReturn(1);

        opsService = new OpsService(
                robotRepository, missionRepository, alarmEventRepository,
                metricSnapshotRepository, healthCheckRepository, reportRecordRepository,
                auditService, actorResolver, clockProvider, new ObjectMapper(),
                natsProvider, minioClient, minioConfig);
    }

    @Test
    void getHealth_natsConnected_returnsHealthy() {
        when(robotRepository.selectCount(any())).thenReturn(5L);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getStatus()).thenReturn("CONNECTED");
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        } catch (Exception e) {
            fail("Mock setup failed", e);
        }

        List<HealthCheckDto> results = opsService.getHealth();

        HealthCheckDto natsCheck = results.stream()
                .filter(c -> "nats".equals(c.component())).findFirst().orElseThrow();
        assertEquals("HEALTHY", natsCheck.status());
        assertNull(natsCheck.errorMessage());
    }

    @Test
    void getHealth_natsDisconnected_returnsUnhealthy() {
        when(robotRepository.selectCount(any())).thenReturn(5L);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(false);
        when(natsManager.tryConnect()).thenReturn(false);
        when(natsManager.getStatus()).thenReturn("DISCONNECTED");
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        } catch (Exception e) {
            fail("Mock setup failed", e);
        }

        List<HealthCheckDto> results = opsService.getHealth();

        HealthCheckDto natsCheck = results.stream()
                .filter(c -> "nats".equals(c.component())).findFirst().orElseThrow();
        assertEquals("UNHEALTHY", natsCheck.status());
        assertNotNull(natsCheck.errorMessage());
    }

    @Test
    void getHealth_natsNotConfigured_returnsUnhealthy() {
        when(robotRepository.selectCount(any())).thenReturn(5L);
        when(natsProvider.getIfAvailable()).thenReturn(null);
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        } catch (Exception e) {
            fail("Mock setup failed", e);
        }

        List<HealthCheckDto> results = opsService.getHealth();

        HealthCheckDto natsCheck = results.stream()
                .filter(c -> "nats".equals(c.component())).findFirst().orElseThrow();
        assertEquals("UNHEALTHY", natsCheck.status());
        assertTrue(natsCheck.errorMessage().contains("not configured"));
    }

    @Test
    void getHealth_minioBucketExists_returnsHealthy() {
        when(robotRepository.selectCount(any())).thenReturn(5L);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getStatus()).thenReturn("CONNECTED");
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        } catch (Exception e) {
            fail("Mock setup failed", e);
        }

        List<HealthCheckDto> results = opsService.getHealth();

        HealthCheckDto minioCheck = results.stream()
                .filter(c -> "minio".equals(c.component())).findFirst().orElseThrow();
        assertEquals("HEALTHY", minioCheck.status());
        assertNull(minioCheck.errorMessage());
    }

    @Test
    void getHealth_minioException_returnsUnhealthy() {
        when(robotRepository.selectCount(any())).thenReturn(5L);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getStatus()).thenReturn("CONNECTED");
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                    .thenThrow(new RuntimeException("Connection refused"));
        } catch (Exception e) {
            fail("Mock setup failed", e);
        }

        List<HealthCheckDto> results = opsService.getHealth();

        HealthCheckDto minioCheck = results.stream()
                .filter(c -> "minio".equals(c.component())).findFirst().orElseThrow();
        assertEquals("UNHEALTHY", minioCheck.status());
        assertTrue(minioCheck.errorMessage().contains("Connection refused"));
    }

    @Test
    void getHealth_natsReconnectsAfterFailure_returnsHealthy() {
        when(robotRepository.selectCount(any())).thenReturn(5L);
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(false).thenReturn(true);
        when(natsManager.tryConnect()).thenReturn(true);
        when(natsManager.getStatus()).thenReturn("DISCONNECTED").thenReturn("CONNECTED");
        try {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        } catch (Exception e) {
            fail("Mock setup failed", e);
        }

        List<HealthCheckDto> results = opsService.getHealth();

        HealthCheckDto natsCheck = results.stream()
                .filter(c -> "nats".equals(c.component())).findFirst().orElseThrow();
        assertEquals("HEALTHY", natsCheck.status());
        verify(natsManager).tryConnect();
    }
}
