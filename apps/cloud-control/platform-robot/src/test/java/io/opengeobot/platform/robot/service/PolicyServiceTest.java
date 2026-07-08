/*
 * Function: Policy service unit tests — CRUD, publish, evaluate
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.Policy;
import io.opengeobot.platform.robot.domain.PolicyRule;
import io.opengeobot.platform.robot.dto.CreatePolicyRequest;
import io.opengeobot.platform.robot.dto.MissionStepDto;
import io.opengeobot.platform.robot.dto.PolicyDto;
import io.opengeobot.platform.robot.dto.PolicyRuleDto;
import io.opengeobot.platform.robot.dto.PolicyVersionDto;
import io.opengeobot.platform.robot.dto.UpdatePolicyRequest;
import io.opengeobot.platform.robot.repository.PolicyAssignmentRepository;
import io.opengeobot.platform.robot.repository.PolicyRepository;
import io.opengeobot.platform.robot.repository.PolicyRuleRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PolicyService}. Covers the SM-POLICY-001 state machine
 * (DRAFT → PUBLISHED → ARCHIVED), versioned publishing, and policy evaluation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PolicyServiceTest {

    @Mock private PolicyRepository policyRepository;
    @Mock private PolicyRuleRepository policyRuleRepository;
    @Mock private PolicyAssignmentRepository policyAssignmentRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private PolicyService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenReturn("pol_001");
        service = new PolicyService(policyRepository, policyRuleRepository,
                policyAssignmentRepository, outboxRepository, auditService,
                actorResolver, clockProvider, idGenerator, objectMapper);
    }

    private Policy createPolicy(String policyId, String name, String status, int version) {
        Policy policy = new Policy();
        policy.setId(1L);
        policy.setPolicyId(policyId);
        policy.setName(name);
        policy.setDescription("Safety policy");
        policy.setStatus(status);
        policy.setCurrentVersion(version);
        policy.setScope("global");
        policy.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        policy.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return policy;
    }

    private PolicyRule createRule(String policyId, int version, String ruleType, String action) {
        PolicyRule rule = new PolicyRule();
        rule.setPolicyId(policyId);
        rule.setVersion(version);
        rule.setRuleType(ruleType);
        rule.setAction(action);
        rule.setPriority(1);
        rule.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return rule;
    }

    @Test
    void create_validRequestInsertsDraftPolicy() {
        when(policyRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        List<PolicyRuleDto> rules = List.of(
                new PolicyRuleDto("restricted_area", "{\"zone_id\":\"z_001\"}", "DENY", 1));

        CreatePolicyRequest request = new CreatePolicyRequest("safety_policy", "desc", rules, "global");

        PolicyDto result = service.create(request);

        assertEquals("pol_001", result.policyId());
        assertEquals("DRAFT", result.status());
        assertEquals(0, result.currentVersion());

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(policyRepository).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus());
        verify(policyRuleRepository).insert(any(PolicyRule.class));
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void create_duplicateNameThrowsConflict() {
        when(policyRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        CreatePolicyRequest request = new CreatePolicyRequest("safety_policy", "desc", List.of(), "global");

        ConflictException ex = assertThrows(ConflictException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("safety_policy"));
        verify(policyRepository, never()).insert(any());
    }

    @Test
    void update_draftPolicyUpdatesFields() {
        Policy policy = createPolicy("pol_001", "policy", "DRAFT", 0);
        when(policyRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(policy);
        when(policyRuleRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PolicyDto result = service.update("pol_001",
                new UpdatePolicyRequest("New Name", "New desc", List.of(), "local"));

        assertEquals("New Name", result.name());
        assertEquals("local", result.scope());
        verify(policyRepository).updateById((Policy) any());
    }

    @Test
    void update_publishedPolicyThrowsConflict() {
        Policy policy = createPolicy("pol_001", "policy", "PUBLISHED", 1);
        when(policyRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(policy);

        assertThrows(ConflictException.class,
                () -> service.update("pol_001", new UpdatePolicyRequest("New", null, null, null)));
    }

    @Test
    void publish_createsVersionedRulesAndTransitionsToPublished() {
        Policy policy = createPolicy("pol_001", "policy", "DRAFT", 0);
        when(policyRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(policy);
        PolicyRule draftRule = createRule("pol_001", 0, "restricted_area", "DENY");
        draftRule.setCondition("{\"zone_id\":\"z_001\"}");
        when(policyRuleRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(draftRule))
                .thenReturn(List.of(draftRule));

        PolicyDto result = service.publish("pol_001");

        assertEquals("PUBLISHED", result.status());
        assertEquals(1, result.currentVersion());

        ArgumentCaptor<PolicyRule> ruleCaptor = ArgumentCaptor.forClass(PolicyRule.class);
        verify(policyRuleRepository).insert(ruleCaptor.capture());
        assertEquals(1, ruleCaptor.getValue().getVersion());
        verify(policyRepository).updateById((Policy) any());
        verify(outboxRepository).save(any());
    }

    @Test
    void publish_archivedThrowsConflict() {
        Policy policy = createPolicy("pol_001", "policy", "ARCHIVED", 1);
        when(policyRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(policy);

        assertThrows(ConflictException.class, () -> service.publish("pol_001"));
    }

    @Test
    void get_notFoundThrowsResourceNotFound() {
        when(policyRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.get("pol_999"));
    }

    @Test
    void evaluate_restrictedAreaDenyReturnsViolation() {
        Policy publishedPolicy = createPolicy("pol_001", "policy", "PUBLISHED", 1);
        when(policyRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(publishedPolicy));
        PolicyRule rule = createRule("pol_001", 1, "restricted_area", "DENY");
        rule.setCondition("{\"zone_id\":\"z_restricted\"}");
        when(policyRuleRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule));

        MissionStepDto step = new MissionStepDto("step_001", "msn_001", "skl_nav", 1,
                Map.of("zone_id", "z_restricted"), null, "PENDING", null, null, null);

        List<String> violations = service.evaluate(List.of(step), "rbt_001");

        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).contains("z_restricted"));
    }

    @Test
    void evaluate_speedLimitWarnReturnsViolation() {
        Policy publishedPolicy = createPolicy("pol_001", "policy", "PUBLISHED", 1);
        when(policyRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(publishedPolicy));
        PolicyRule rule = createRule("pol_001", 1, "speed_limit", "WARN");
        rule.setCondition("{\"max_speed\":2.0}");
        when(policyRuleRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule));

        MissionStepDto step = new MissionStepDto("step_001", "msn_001", "skl_nav", 1,
                Map.of("speed", 3.5), null, "PENDING", null, null, null);

        List<String> violations = service.evaluate(List.of(step), "rbt_001");

        assertFalse(violations.isEmpty());
        assertTrue(violations.get(0).contains("speed limit"));
    }

    @Test
    void evaluate_compliantStepsReturnsNoViolations() {
        Policy publishedPolicy = createPolicy("pol_001", "policy", "PUBLISHED", 1);
        when(policyRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(publishedPolicy));
        PolicyRule rule = createRule("pol_001", 1, "restricted_area", "DENY");
        rule.setCondition("{\"zone_id\":\"z_restricted\"}");
        when(policyRuleRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule));

        MissionStepDto step = new MissionStepDto("step_001", "msn_001", "skl_nav", 1,
                Map.of("zone_id", "z_safe"), null, "PENDING", null, null, null);

        List<String> violations = service.evaluate(List.of(step), "rbt_001");

        assertTrue(violations.isEmpty());
    }

    @Test
    void evaluate_noActivePoliciesReturnsEmpty() {
        when(policyRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<String> violations = service.evaluate(List.of(), "rbt_001");

        assertTrue(violations.isEmpty());
    }

    @Test
    void listVersions_policyNotFoundThrowsResourceNotFound() {
        when(policyRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listVersions("pol_999", PageRequest.of(1, 10)));
    }

    @Test
    void listVersions_returnsPagedVersions() {
        when(policyRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        PolicyRule rule = createRule("pol_001", 1, "restricted_area", "DENY");
        when(policyRuleRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule));

        PageResult<PolicyVersionDto> result = service.listVersions("pol_001", PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals(1, result.items().get(0).version());
    }
}
