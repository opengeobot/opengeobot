/*
 * Function: Mission lifecycle web-layer tests - create, plan, approve, execute
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.robot.dto.MissionApprovalDto;
import io.opengeobot.platform.robot.dto.MissionDto;
import io.opengeobot.platform.robot.service.MissionService;
import io.opengeobot.platform.robot.web.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for the mission lifecycle flow. Tests the full cycle:
 * create -> plan -> submit approval -> approve -> start -> complete.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MissionLifecycleWebLayerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private MissionService missionService;

    private MissionDto createMissionDto(String status) {
        return new MissionDto("msn_001", "Test Mission", "desc", "rbt_001",
                status, "NORMAL", null, null, null, null,
                "user_001", null, null, "trace_001", null);
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.create"})
    void createMission_validRequestReturns201() throws Exception {
        when(missionService.create(any())).thenReturn(createMissionDto("PENDING"));

        mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test Mission","description":"desc","robot_id":"rbt_001","priority":"NORMAL","steps":[{"skill_id":"skl_nav","step_order":1,"params":{}}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mission_id").value("msn_001"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.create"})
    void revisePlan_validRequestReturns200() throws Exception {
        when(missionService.revisePlan(any(), any())).thenReturn(createMissionDto("READY"));

        mockMvc.perform(post("/api/v1/missions/msn_001/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"steps":[{"skill_id":"skl_nav","step_order":1,"params":{"target":"room_a"}}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.create"})
    void submitApproval_fromReadyReturns200() throws Exception {
        MissionApprovalDto approval = new MissionApprovalDto(
                "msn_001", "user_001", "PENDING", null, null, null);
        when(missionService.submitApproval("msn_001")).thenReturn(approval);

        mockMvc.perform(post("/api/v1/missions/msn_001/submit-approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.approve"})
    void approveMission_pendingApprovalReturnsApproved() throws Exception {
        MissionApprovalDto approval = new MissionApprovalDto(
                "msn_001", "user_001", "APPROVED", "Looks good", null, null);
        when(missionService.approve(any(), any())).thenReturn(approval);

        mockMvc.perform(post("/api/v1/missions/msn_001/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"Looks good"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.approve"})
    void rejectMission_pendingApprovalReturnsRejected() throws Exception {
        MissionApprovalDto approval = new MissionApprovalDto(
                "msn_001", "user_001", "REJECTED", "Safety concern", null, null);
        when(missionService.reject(any(), any())).thenReturn(approval);

        mockMvc.perform(post("/api/v1/missions/msn_001/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"comment":"Safety concern"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.create"})
    void startMission_fromReadyReturnsExecuting() throws Exception {
        when(missionService.start("msn_001")).thenReturn(createMissionDto("EXECUTING"));

        mockMvc.perform(post("/api/v1/missions/msn_001/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTING"));
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.create"})
    void startMission_fromPendingReturns409() throws Exception {
        when(missionService.start("msn_001"))
                .thenThrow(new ConflictException("Mission not in READY state"));

        mockMvc.perform(post("/api/v1/missions/msn_001/start"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.pause"})
    void pauseMission_fromExecutingReturnsPaused() throws Exception {
        when(missionService.pause("msn_001")).thenReturn(createMissionDto("PAUSED"));

        mockMvc.perform(post("/api/v1/missions/msn_001/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.pause"})
    void resumeMission_fromPausedReturnsExecuting() throws Exception {
        when(missionService.resume("msn_001")).thenReturn(createMissionDto("EXECUTING"));

        mockMvc.perform(post("/api/v1/missions/msn_001/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTING"));
    }

    @Test
    @WithMockUser(authorities = {"mission.mission.cancel"})
    void cancelMission_fromExecutingReturnsCancelled() throws Exception {
        when(missionService.cancel("msn_001")).thenReturn(createMissionDto("CANCELLED"));

        mockMvc.perform(post("/api/v1/missions/msn_001/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
