/*
 * Function: OTA deployment integration tests — create campaign, deploy, status, rollback
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.robot.dto.DeploymentRecordDto;
import io.opengeobot.platform.robot.dto.ReleaseCampaignDto;
import io.opengeobot.platform.robot.service.OtaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the OTA deployment flow. Tests the full cycle:
 * create campaign → check status → rollback. Follows SM-OTA-001 state machine
 * (CREATED → IN_PROGRESS → COMPLETED / ROLLED_BACK).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OtaDeploymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private OtaService otaService;

    private ReleaseCampaignDto createCampaignDto(String status) {
        return new ReleaseCampaignDto("ota_001", "pkg_001", 10, status,
                List.of("rbt_001", "rbt_002"), null, null, "user_001",
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @WithMockUser(authorities = {"ops.ota.manage"})
    void createCampaign_validRequestReturns201() throws Exception {
        when(otaService.createCampaign(any())).thenReturn(createCampaignDto("CREATED"));

        mockMvc.perform(post("/api/v1/ota/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"package_id":"pkg_001","target_robots":["rbt_001","rbt_002"],"canary_percent":10}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaign_id").value("ota_001"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.canary_percent").value(10));
    }

    @Test
    @WithMockUser(authorities = {"ops.ota.manage"})
    void createCampaign_blankPackageIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/ota/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"package_id":"","target_robots":["rbt_001"],"canary_percent":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"ops.ota.manage"})
    void createCampaign_emptyTargetRobotsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/ota/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"package_id":"pkg_001","target_robots":[],"canary_percent":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = {"ops.ota.read"})
    void getCampaign_existingReturnsDetail() throws Exception {
        ReleaseCampaignDto campaign = createCampaignDto("IN_PROGRESS");
        DeploymentRecordDto deployment = new DeploymentRecordDto(
                "dep_001", "ota_001", "rbt_001", "DEPLOYED",
                OffsetDateTime.now(ZoneOffset.UTC), null, null);
        OtaService.CampaignDetail detail = new OtaService.CampaignDetail(campaign, List.of(deployment));
        when(otaService.getCampaign("ota_001")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/ota/campaigns/ota_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaign.campaign_id").value("ota_001"))
                .andExpect(jsonPath("$.campaign.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.deployments[0].robot_id").value("rbt_001"))
                .andExpect(jsonPath("$.deployments[0].status").value("DEPLOYED"));
    }

    @Test
    @WithMockUser(authorities = {"ops.ota.read"})
    void getDeploymentStatus_returnsDeployments() throws Exception {
        DeploymentRecordDto dep1 = new DeploymentRecordDto(
                "dep_001", "ota_001", "rbt_001", "DEPLOYED",
                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC), null);
        DeploymentRecordDto dep2 = new DeploymentRecordDto(
                "dep_002", "ota_001", "rbt_002", "PENDING",
                null, null, null);
        when(otaService.getDeploymentStatus("ota_001")).thenReturn(List.of(dep1, dep2));

        mockMvc.perform(get("/api/v1/ota/campaigns/ota_001/deployments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].robot_id").value("rbt_001"))
                .andExpect(jsonPath("$[0].status").value("DEPLOYED"))
                .andExpect(jsonPath("$[1].robot_id").value("rbt_002"))
                .andExpect(jsonPath("$[1].status").value("PENDING"));
    }

    @Test
    @WithMockUser(authorities = {"ops.ota.manage"})
    void rollbackCampaign_inProgressReturnsRolledBack() throws Exception {
        when(otaService.rollback("ota_001")).thenReturn(createCampaignDto("ROLLED_BACK"));

        mockMvc.perform(post("/api/v1/ota/campaigns/ota_001/rollback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ROLLED_BACK"));
    }

    @Test
    @WithMockUser(authorities = {"ops.ota.read"})
    void listCampaigns_returnsPagedResults() throws Exception {
        when(otaService.listCampaigns(any(), any()))
                .thenReturn(new io.opengeobot.platform.common.page.PageResult<>(
                        List.of(createCampaignDto("CREATED")), 1, 1, 20));

        mockMvc.perform(get("/api/v1/ota/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].campaign_id").value("ota_001"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void createCampaign_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/ota/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"package_id":"pkg_001","target_robots":["rbt_001"],"canary_percent":0}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ops.ota.read"})
    void createCampaign_withoutManagePermissionReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/ota/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"package_id":"pkg_001","target_robots":["rbt_001"],"canary_percent":0}
                                """))
                .andExpect(status().isForbidden());
    }
}
