/*
 * Function: Permission enforcement web-layer tests - 403 vs 200 based on authorities
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.integration;

import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.robot.dto.MissionTemplateDto;
import io.opengeobot.platform.robot.dto.RobotDto;
import io.opengeobot.platform.robot.dto.RobotGroupDto;
import io.opengeobot.platform.robot.dto.RobotModelDto;
import io.opengeobot.platform.robot.service.MissionService;
import io.opengeobot.platform.robot.service.RobotGroupService;
import io.opengeobot.platform.robot.service.RobotModelService;
import io.opengeobot.platform.robot.service.RobotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for permission enforcement. Verifies that endpoints return
 * 403 without proper permissions and 200 with proper permissions.
 *
 * <p>Uses @WithMockUser to simulate authenticated users with specific authorities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionEnforcementWebLayerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private RobotService robotService;
    @MockBean private MissionService missionService;
    @MockBean private RobotGroupService groupService;
    @MockBean private RobotModelService modelService;

    @Test
    @WithMockUser(authorities = {"robot.robot.read"})
    void listRobots_withReadPermissionReturns200() throws Exception {
        when(robotService.list(any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/v1/robots"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"some.other.permission"})
    void listRobots_withoutReadPermissionReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/robots"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRobots_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/robots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.register"})
    void createRobot_withRegisterPermissionReturns201() throws Exception {
        RobotDto robot = new RobotDto("rbt_001", "TestBot", "unitree-go2", "SN-001",
                "OFFLINE", "org_001", List.of(), null, null, null, null);
        when(robotService.create(any())).thenReturn(robot);

        mockMvc.perform(post("/api/v1/robots")
                        .contentType("application/json")
                        .content("""
                                {"name":"TestBot","model_id":"unitree-go2","serial_number":"SN-001","org_id":"org_001","capabilities":[]}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.read"})
    void createRobot_withOnlyReadPermissionReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/robots")
                        .contentType("application/json")
                        .content("""
                                {"name":"TestBot","model_id":"unitree-go2","serial_number":"SN-001","org_id":"org_001","capabilities":[]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.read"})
    void getRobot_withReadPermissionReturns200() throws Exception {
        RobotDto robot = new RobotDto("rbt_001", "TestBot", "unitree-go2", "SN-001",
                "OFFLINE", "org_001", List.of(), null, null, null, null);
        when(robotService.getByRobotId("rbt_001")).thenReturn(robot);

        mockMvc.perform(get("/api/v1/robots/rbt_001"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"robot.robot.register"})
    void getRobot_withoutReadPermissionReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/robots/rbt_001"))
                .andExpect(status().isForbidden());
    }

    // ----- MissionTemplateController (/api/v1/mission-templates) -----

    @Test
    @WithMockUser(authorities = {"mission.template.read"})
    void listMissionTemplates_withReadPermissionReturns200() throws Exception {
        when(missionService.listTemplates(anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/v1/mission-templates"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"some.other.permission"})
    void listMissionTemplates_withoutReadPermissionReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/mission-templates"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"mission.template.manage"})
    void createMissionTemplate_withManagePermissionReturns201() throws Exception {
        MissionTemplateDto template = new MissionTemplateDto("tpl_001", "Patrol Template",
                "desc", List.of(), "user_001", null, null);
        when(missionService.createTemplate(any())).thenReturn(template);

        mockMvc.perform(post("/api/v1/mission-templates")
                        .contentType("application/json")
                        .content("""
                                {"name":"Patrol Template","steps":[{"skill_id":"skill_001","step_order":1}]}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"mission.template.read"})
    void createMissionTemplate_withOnlyReadPermissionReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/mission-templates")
                        .contentType("application/json")
                        .content("""
                                {"name":"Patrol Template","steps":[{"skill_id":"skill_001","step_order":1}]}
                                """))
                .andExpect(status().isForbidden());
    }

    // ----- RobotGroupController (/api/v1/robot-groups) -----

    @Test
    @WithMockUser(authorities = {"robot.group.read"})
    void listRobotGroups_withReadPermissionReturns200() throws Exception {
        when(groupService.list(any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/v1/robot-groups"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"some.other.permission"})
    void listRobotGroups_withoutReadPermissionReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/robot-groups"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"robot.group.manage"})
    void createRobotGroup_withManagePermissionReturns201() throws Exception {
        RobotGroupDto group = new RobotGroupDto("grp_001", null, "Group1",
                "desc", "/grp_001", null, null);
        when(groupService.create(any())).thenReturn(group);

        mockMvc.perform(post("/api/v1/robot-groups")
                        .contentType("application/json")
                        .content("""
                                {"group_name":"Group1"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"robot.group.read"})
    void createRobotGroup_withOnlyReadPermissionReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/robot-groups")
                        .contentType("application/json")
                        .content("""
                                {"group_name":"Group1"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ----- RobotModelController (/api/v1/robot-models) -----

    @Test
    @WithMockUser(authorities = {"robot.model.read"})
    void listRobotModels_withReadPermissionReturns200() throws Exception {
        when(modelService.list(any()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/v1/robot-models"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"some.other.permission"})
    void listRobotModels_withoutReadPermissionReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/robot-models"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"robot.model.manage"})
    void createRobotModel_withManagePermissionReturns201() throws Exception {
        RobotModelDto model = new RobotModelDto("mdl_001", "Go2", "Unitree",
                "desc", "[]", null, null);
        when(modelService.create(any())).thenReturn(model);

        mockMvc.perform(post("/api/v1/robot-models")
                        .contentType("application/json")
                        .content("""
                                {"model_name":"Go2"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = {"robot.model.read"})
    void createRobotModel_withOnlyReadPermissionReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/robot-models")
                        .contentType("application/json")
                        .content("""
                                {"model_name":"Go2"}
                                """))
                .andExpect(status().isForbidden());
    }
}
