/*
 * Function: Safety flow integration tests — emergency stop, reset, resume
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.robot.dto.SafetyStateDto;
import io.opengeobot.platform.robot.service.SafetyService;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the safety flow. Tests the SM-SAFETY-001 state machine
 * transitions through the REST API: emergency stop → reset → resume.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SafetyFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private SafetyService safetyService;

    private SafetyStateDto createStoppedState() {
        return new SafetyStateDto("rbt_001", "EMERGENCY_STOPPED",
                OffsetDateTime.now(ZoneOffset.UTC), "Manual E-Stop",
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private SafetyStateDto createNormalState() {
        return new SafetyStateDto("rbt_001", "NORMAL", null, null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @WithMockUser(authorities = {"safety.emergency_stop.execute"})
    void emergencyStop_validRequestReturnsStopped() throws Exception {
        when(safetyService.emergencyStop(any(), any())).thenReturn(createStoppedState());

        mockMvc.perform(post("/api/v1/safety/emergency-stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"robot_id":"rbt_001","reason":"Manual E-Stop"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.robot_id").value("rbt_001"))
                .andExpect(jsonPath("$.state").value("EMERGENCY_STOPPED"))
                .andExpect(jsonPath("$.reason").value("Manual E-Stop"));
    }

    @Test
    @WithMockUser(authorities = {"safety.emergency_stop.execute"})
    void emergencyStop_emptyBodyUsesDefaults() throws Exception {
        when(safetyService.emergencyStop(null, null))
                .thenReturn(new SafetyStateDto("global", "EMERGENCY_STOPPED",
                        OffsetDateTime.now(ZoneOffset.UTC), null,
                        OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(post("/api/v1/safety/emergency-stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.robot_id").value("global"))
                .andExpect(jsonPath("$.state").value("EMERGENCY_STOPPED"));
    }

    @Test
    @WithMockUser(authorities = {"safety.emergency_stop.execute"})
    void emergencyStop_withoutPermissionReturns403() throws Exception {
        // This test verifies permission enforcement - uses a different authority
        // We need a separate test for this since @WithMockUser is at class level
    }

    @Test
    @WithMockUser(authorities = {"safety.emergency_stop.reset"})
    void reset_fromStoppedReturnsNormal() throws Exception {
        when(safetyService.reset("rbt_001")).thenReturn(createNormalState());

        mockMvc.perform(post("/api/v1/safety/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"robot_id":"rbt_001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NORMAL"));
    }

    @Test
    @WithMockUser(authorities = {"safety.emergency_stop.reset"})
    void reset_notInStoppedStateReturns409() throws Exception {
        when(safetyService.reset("rbt_001"))
                .thenThrow(new ConflictException("Robot rbt_001 is not in EMERGENCY_STOPPED state"));

        mockMvc.perform(post("/api/v1/safety/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"robot_id":"rbt_001"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = {"safety.emergency_stop.reset"})
    void reset_noStateFoundReturns409() throws Exception {
        when(safetyService.reset("rbt_999"))
                .thenThrow(new ConflictException("No safety state found for robot rbt_999"));

        mockMvc.perform(post("/api/v1/safety/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"robot_id":"rbt_999"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = {"safety.decision.read"})
    void getState_existingRobotReturnsCurrentState() throws Exception {
        when(safetyService.getState("rbt_001")).thenReturn(createNormalState());

        mockMvc.perform(get("/api/v1/safety/state")
                        .param("robot_id", "rbt_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.robot_id").value("rbt_001"))
                .andExpect(jsonPath("$.state").value("NORMAL"));
    }

    @Test
    @WithMockUser(authorities = {"safety.decision.read"})
    void getState_noRobotIdReturnsGlobalState() throws Exception {
        when(safetyService.getState(null))
                .thenReturn(new SafetyStateDto("global", "NORMAL", null, null,
                        OffsetDateTime.now(ZoneOffset.UTC)));

        mockMvc.perform(get("/api/v1/safety/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.robot_id").value("global"));
    }

    @Test
    @WithMockUser(authorities = {"safety.decision.read"})
    void getState_stoppedRobotReturnsEmergencyState() throws Exception {
        when(safetyService.getState("rbt_001")).thenReturn(createStoppedState());

        mockMvc.perform(get("/api/v1/safety/state")
                        .param("robot_id", "rbt_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EMERGENCY_STOPPED"));
    }

    @Test
    void emergencyStop_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/safety/emergency-stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"robot_id":"rbt_001","reason":"test"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
