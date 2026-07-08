/*
 * Function: Robot management integration tests — register, query, update, delete
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.RobotDto;
import io.opengeobot.platform.robot.service.RobotService;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the robot management flow. Tests the full CRUD cycle
 * (register → query → update → delete) through the REST API with a mocked
 * {@link RobotService}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RobotManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RobotService robotService;

    private RobotDto createRobotDto() {
        return new RobotDto("rbt_001", "TestBot", "unitree-go2", "SN-001",
                "OFFLINE", "org_001", List.of(), null, null, null, null);
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.register"})
    void registerRobot_validRequestReturns201() throws Exception {
        when(robotService.create(any())).thenReturn(createRobotDto());

        mockMvc.perform(post("/api/v1/robots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"TestBot","model_id":"unitree-go2","serial_number":"SN-001","org_id":"org_001","capabilities":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.robot_id").value("rbt_001"))
                .andExpect(jsonPath("$.name").value("TestBot"))
                .andExpect(jsonPath("$.status").value("OFFLINE"));
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.register"})
    void registerRobot_duplicateSerialReturns409() throws Exception {
        when(robotService.create(any()))
                .thenThrow(new ConflictException("Serial number already exists: SN-001"));

        mockMvc.perform(post("/api/v1/robots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"TestBot","model_id":"unitree-go2","serial_number":"SN-001","org_id":"org_001","capabilities":[]}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.read"})
    void queryRobot_existingReturns200() throws Exception {
        when(robotService.getByRobotId("rbt_001")).thenReturn(createRobotDto());

        mockMvc.perform(get("/api/v1/robots/rbt_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.robot_id").value("rbt_001"))
                .andExpect(jsonPath("$.serial_number").value("SN-001"));
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.read"})
    void queryRobot_notFoundReturns404() throws Exception {
        when(robotService.getByRobotId("rbt_999"))
                .thenThrow(new ResourceNotFoundException("Robot not found: rbt_999"));

        mockMvc.perform(get("/api/v1/robots/rbt_999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.register"})
    void updateRobot_validRequestReturns200() throws Exception {
        RobotDto updated = new RobotDto("rbt_001", "UpdatedBot", "unitree-go2", "SN-001",
                "OFFLINE", "org_002", List.of(), null, null, null, null);
        when(robotService.update(any(), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/robots/rbt_001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"UpdatedBot","org_id":"org_002"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("UpdatedBot"))
                .andExpect(jsonPath("$.org_id").value("org_002"));
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.register"})
    void deleteRobot_offlineReturns204() throws Exception {
        doNothing().when(robotService).delete("rbt_001");

        mockMvc.perform(delete("/api/v1/robots/rbt_001"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.register"})
    void deleteRobot_onlineReturns409() throws Exception {
        doThrow(new ConflictException("Robot is not in a terminal status"))
                .when(robotService).delete("rbt_001");

        mockMvc.perform(delete("/api/v1/robots/rbt_001"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.register"})
    void updateStatus_validTransitionReturns200() throws Exception {
        doNothing().when(robotService).updateStatus(any(), any());

        mockMvc.perform(patch("/api/v1/robots/rbt_001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ONLINE","reason":"Heartbeat received"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.read"})
    void listRobots_returnsPagedResults() throws Exception {
        when(robotService.list(any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(createRobotDto()), 1, 1, 20));

        mockMvc.perform(get("/api/v1/robots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].robot_id").value("rbt_001"))
                .andExpect(jsonPath("$.total").value(1));
    }
}
