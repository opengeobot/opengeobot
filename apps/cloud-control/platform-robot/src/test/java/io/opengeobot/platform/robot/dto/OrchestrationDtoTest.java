/*
 * Function: Orchestration DTO serialization/deserialization tests
 * Time: 2026-07-16
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that orchestration DTOs serialize and deserialize correctly between
 * the Java cloud-control and the Python agent-runtime. Verifies snake_case
 * JSON field naming and round-trip fidelity.
 */
class OrchestrationDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void missionContextDto_serializesToSnakeCase() throws Exception {
        MissionContextDto dto = new MissionContextDto(
                "msn_001", "trace_abc", "rbt_001", "Navigate to room A",
                Map.of("priority", "HIGH"), "2026-07-16T10:00:00Z");

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"mission_id\""));
        assertTrue(json.contains("\"trace_id\""));
        assertTrue(json.contains("\"robot_id\""));
        assertTrue(json.contains("\"requested_at\""));
        assertFalse(json.contains("\"missionId\""));
        assertFalse(json.contains("\"traceId\""));
    }

    @Test
    void planProposalDto_deserializesFromAgentRuntimeResponse() throws Exception {
        String json = """
                {
                  "plan_id": "plan_001",
                  "mission_id": "msn_001",
                  "trace_id": "trace_abc",
                  "robot_id": "rbt_001",
                  "steps": [
                    {
                      "step_id": "plan_001_step_0",
                      "skill_id": "skl_navigate",
                      "params": {"target": "room_a"},
                      "description": "Navigate to room A",
                      "valid": true,
                      "validation_error": null
                    }
                  ],
                  "confidence": 0.85,
                  "rationale": "Direct path available",
                  "is_trusted": false,
                  "error": null,
                  "generated_at": "2026-07-16T10:00:00Z"
                }
                """;

        PlanProposalDto dto = objectMapper.readValue(json, PlanProposalDto.class);

        assertEquals("plan_001", dto.planId());
        assertEquals("msn_001", dto.missionId());
        assertEquals("rbt_001", dto.robotId());
        assertEquals(0.85, dto.confidence(), 0.001);
        assertFalse(dto.isTrusted());
        assertNull(dto.error());
        assertNotNull(dto.steps());
        assertEquals(1, dto.steps().size());
        assertEquals("skl_navigate", dto.steps().get(0).skillId());
        assertEquals("Navigate to room A", dto.steps().get(0).description());
        assertTrue(dto.steps().get(0).valid());
    }

    @Test
    void planProposalDto_withError_deserializesCorrectly() throws Exception {
        String json = """
                {
                  "plan_id": "",
                  "mission_id": "msn_002",
                  "trace_id": "trace_def",
                  "robot_id": "rbt_002",
                  "steps": [],
                  "confidence": 0.0,
                  "rationale": "",
                  "is_trusted": false,
                  "error": "QwenPaw API timed out",
                  "generated_at": "2026-07-16T10:01:00Z"
                }
                """;

        PlanProposalDto dto = objectMapper.readValue(json, PlanProposalDto.class);

        assertEquals("", dto.planId());
        assertEquals("msn_002", dto.missionId());
        assertEquals("QwenPaw API timed out", dto.error());
        assertFalse(dto.isTrusted());
        assertNotNull(dto.steps());
        assertTrue(dto.steps().isEmpty());
    }

    @Test
    void planProposalDto_ignoresUnknownFields() throws Exception {
        String json = """
                {
                  "plan_id": "plan_003",
                  "mission_id": "msn_003",
                  "trace_id": "",
                  "robot_id": "",
                  "steps": [],
                  "confidence": 0.5,
                  "rationale": "",
                  "is_trusted": false,
                  "error": null,
                  "generated_at": "2026-07-16T10:02:00Z",
                  "extra_field": "ignored"
                }
                """;

        assertDoesNotThrow(() -> objectMapper.readValue(json, PlanProposalDto.class));
    }

    @Test
    void edgeCommandDto_serializesToSnakeCase() throws Exception {
        EdgeCommandDto dto = new EdgeCommandDto(
                "cmd_001", "trace_abc", "start_mission", "msn_001", null,
                Map.of("lease_id", "lease_001"), "2026-07-16T10:00:00Z");

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"command_id\""));
        assertTrue(json.contains("\"trace_id\""));
        assertTrue(json.contains("\"command_type\""));
        assertTrue(json.contains("\"mission_id\""));
        assertTrue(json.contains("\"issued_at\""));
        assertFalse(json.contains("\"commandId\""));
        assertFalse(json.contains("\"commandType\""));
    }

    @Test
    void edgeCommandDto_roundTripPreservesData() throws Exception {
        EdgeCommandDto original = new EdgeCommandDto(
                "cmd_002", "trace_xyz", "execute_skill", "msn_004", "skl_pickup",
                Map.of("target_object", "cup"), "2026-07-16T11:00:00Z");

        String json = objectMapper.writeValueAsString(original);
        EdgeCommandDto deserialized = objectMapper.readValue(json, EdgeCommandDto.class);

        assertEquals(original.commandId(), deserialized.commandId());
        assertEquals(original.traceId(), deserialized.traceId());
        assertEquals(original.commandType(), deserialized.commandType());
        assertEquals(original.missionId(), deserialized.missionId());
        assertEquals(original.skillId(), deserialized.skillId());
        assertEquals(original.issuedAt(), deserialized.issuedAt());
        assertEquals(original.params().get("target_object"), deserialized.params().get("target_object"));
    }

    @Test
    void planStepDto_defaultsStepOrderToZeroWhenAbsent() throws Exception {
        String json = """
                {
                  "step_id": "step_001",
                  "skill_id": "skl_navigate",
                  "params": {},
                  "description": "Move forward"
                }
                """;

        PlanStepDto dto = objectMapper.readValue(json, PlanStepDto.class);

        assertEquals("step_001", dto.stepId());
        assertEquals("skl_navigate", dto.skillId());
        assertEquals(0, dto.stepOrder());
    }
}
