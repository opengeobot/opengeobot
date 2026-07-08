/*
 * Function: OtaService unit tests — real deployment via NATS request-reply
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.NatsConnectionManager;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.config.MinioConfig;
import io.opengeobot.platform.robot.domain.DeploymentRecord;
import io.opengeobot.platform.robot.domain.FirmwarePackage;
import io.opengeobot.platform.robot.domain.ReleaseCampaign;
import io.opengeobot.platform.robot.dto.ReleaseCampaignDto;
import io.opengeobot.platform.robot.repository.DeploymentRecordRepository;
import io.opengeobot.platform.robot.repository.FirmwarePackageRepository;
import io.opengeobot.platform.robot.repository.ReleaseCampaignRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link OtaService} deployment logic. Verifies that
 * deployments route to edge gateways via NATS request-reply and that
 * deployment status reflects the real edge response (SUCCESS / FAILED).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtaServiceTest {

    @Mock private FirmwarePackageRepository packageRepository;
    @Mock private ReleaseCampaignRepository campaignRepository;
    @Mock private DeploymentRecordRepository deploymentRepository;
    @Mock private MinioClient minioClient;
    @Mock private MinioConfig minioConfig;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;
    @Mock private ObjectProvider<NatsConnectionManager> natsProvider;
    @Mock private NatsConnectionManager natsManager;
    @Mock private Connection natsConnection;
    @Mock private Message natsReply;

    private OtaService otaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(actorResolver.currentActor()).thenReturn("test-user");
        when(actorResolver.currentTraceId()).thenReturn("trace-001");
        when(idGenerator.generate(anyString())).thenReturn("id-001");
        when(minioConfig.getBucket()).thenReturn("test-bucket");
        when(campaignRepository.updateById(any(ReleaseCampaign.class))).thenReturn(1);
        when(deploymentRepository.updateById(any(DeploymentRecord.class))).thenReturn(1);
        doNothing().when(outboxRepository).save(any());

        otaService = new OtaService(
                packageRepository, campaignRepository, deploymentRepository,
                minioClient, minioConfig, outboxRepository, auditService,
                actorResolver, clockProvider, idGenerator, objectMapper,
                natsProvider, 300000);
    }

    private FirmwarePackage createPackage() {
        FirmwarePackage pkg = new FirmwarePackage();
        pkg.setPackageId("pkg_001");
        pkg.setName("firmware-test");
        pkg.setVersion("1.0.0");
        pkg.setType("FIRMWARE");
        pkg.setFilePath("ota/pkg_001.bin");
        pkg.setFileSize(1024L);
        pkg.setChecksum("abc123");
        return pkg;
    }

    private ReleaseCampaign createCampaign(String status) {
        ReleaseCampaign campaign = new ReleaseCampaign();
        campaign.setCampaignId("ota_001");
        campaign.setPackageId("pkg_001");
        campaign.setCanaryPercent(100);
        campaign.setStatus(status);
        campaign.setTargetRobotIds(List.of("robot_001"));
        return campaign;
    }

    private DeploymentRecord createDeployment(String robotId) {
        DeploymentRecord record = new DeploymentRecord();
        record.setRecordId("dep_001");
        record.setCampaignId("ota_001");
        record.setRobotId(robotId);
        record.setStatus("PENDING");
        return record;
    }

    private void setupPackageLookup() {
        when(packageRepository.selectOne(any())).thenReturn(createPackage());
    }

    @Test
    void startDeployment_edgeSuccess_marksDeploymentSuccess() throws Exception {
        ReleaseCampaign campaign = createCampaign("CREATED");
        when(campaignRepository.selectOne(any())).thenReturn(campaign);
        when(deploymentRepository.selectList(any()))
                .thenReturn(List.of(createDeployment("robot_001")));
        setupPackageLookup();
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getConnection()).thenReturn(natsConnection);
        String response = "{\"status\":\"SUCCESS\"}";
        when(natsReply.getData()).thenReturn(response.getBytes(StandardCharsets.UTF_8));
        when(natsConnection.request(anyString(), any(byte[].class), any(java.time.Duration.class)))
                .thenReturn(natsReply);

        ReleaseCampaignDto result = otaService.startDeployment("ota_001");

        assertEquals("COMPLETED", result.status());
        verify(natsConnection).request(eq("opengeobot.edge.robot_001.ota.deploy"),
                any(byte[].class), any(java.time.Duration.class));
    }

    @Test
    void startDeployment_edgeFailure_marksDeploymentFailed() throws Exception {
        ReleaseCampaign campaign = createCampaign("CREATED");
        when(campaignRepository.selectOne(any())).thenReturn(campaign);
        when(deploymentRepository.selectList(any()))
                .thenReturn(List.of(createDeployment("robot_001")));
        setupPackageLookup();
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getConnection()).thenReturn(natsConnection);
        String response = "{\"status\":\"FAILED\",\"message\":\"checksum mismatch\"}";
        when(natsReply.getData()).thenReturn(response.getBytes(StandardCharsets.UTF_8));
        when(natsConnection.request(anyString(), any(byte[].class), any(java.time.Duration.class)))
                .thenReturn(natsReply);

        ReleaseCampaignDto result = otaService.startDeployment("ota_001");

        assertEquals("FAILED", result.status());
    }

    @Test
    void startDeployment_natsNotAvailable_marksAllFailed() throws Exception {
        ReleaseCampaign campaign = createCampaign("CREATED");
        when(campaignRepository.selectOne(any())).thenReturn(campaign);
        when(deploymentRepository.selectList(any()))
                .thenReturn(List.of(createDeployment("robot_001")));
        setupPackageLookup();
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(false);
        when(natsManager.tryConnect()).thenReturn(false);

        ReleaseCampaignDto result = otaService.startDeployment("ota_001");

        assertEquals("FAILED", result.status());
        verify(natsConnection, never()).request(anyString(), any(byte[].class), any(java.time.Duration.class));
    }

    @Test
    void startDeployment_edgeTimeout_marksFailed() throws Exception {
        ReleaseCampaign campaign = createCampaign("CREATED");
        when(campaignRepository.selectOne(any())).thenReturn(campaign);
        when(deploymentRepository.selectList(any()))
                .thenReturn(List.of(createDeployment("robot_001")));
        setupPackageLookup();
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getConnection()).thenReturn(natsConnection);
        when(natsConnection.request(anyString(), any(byte[].class), any(java.time.Duration.class)))
                .thenReturn(null);

        ReleaseCampaignDto result = otaService.startDeployment("ota_001");

        assertEquals("FAILED", result.status());
    }

    @Test
    void startDeployment_partialFailure_campaignCompleted() throws Exception {
        ReleaseCampaign campaign = createCampaign("CREATED");
        campaign.setTargetRobotIds(List.of("robot_001", "robot_002"));
        when(campaignRepository.selectOne(any())).thenReturn(campaign);
        DeploymentRecord dep1 = createDeployment("robot_001");
        DeploymentRecord dep2 = createDeployment("robot_002");
        dep2.setRecordId("dep_002");
        when(deploymentRepository.selectList(any())).thenReturn(List.of(dep1, dep2));
        setupPackageLookup();
        when(natsProvider.getIfAvailable()).thenReturn(natsManager);
        when(natsManager.isConnected()).thenReturn(true);
        when(natsManager.getConnection()).thenReturn(natsConnection);
        String successResponse = "{\"status\":\"SUCCESS\"}";
        String failResponse = "{\"status\":\"FAILED\",\"message\":\"disk full\"}";
        when(natsReply.getData())
                .thenReturn(successResponse.getBytes(StandardCharsets.UTF_8))
                .thenReturn(failResponse.getBytes(StandardCharsets.UTF_8));
        when(natsConnection.request(anyString(), any(byte[].class), any(java.time.Duration.class)))
                .thenReturn(natsReply);

        ReleaseCampaignDto result = otaService.startDeployment("ota_001");

        assertEquals("COMPLETED", result.status());
    }
}
