/*
 * Function: Memory service unit tests — case recording, suggestions, feedback
 * Time: 2026-07-06
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.FailureCase;
import io.opengeobot.platform.robot.domain.ImprovementSuggestion;
import io.opengeobot.platform.robot.domain.TaskCase;
import io.opengeobot.platform.robot.dto.FeedbackRequest;
import io.opengeobot.platform.robot.dto.ImprovementSuggestionDto;
import io.opengeobot.platform.robot.dto.TaskCaseDto;
import io.opengeobot.platform.robot.repository.FailureCaseRepository;
import io.opengeobot.platform.robot.repository.ImprovementSuggestionRepository;
import io.opengeobot.platform.robot.repository.TaskCaseRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
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
 * Unit tests for {@link MemoryService}. Covers task case recording, failure
 * case creation, improvement suggestion generation, feedback submission, and
 * the SM-IMPROVE-001 state machine (PENDING → ACCEPTED).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryServiceTest {

    @Mock private TaskCaseRepository taskCaseRepository;
    @Mock private FailureCaseRepository failureCaseRepository;
    @Mock private ImprovementSuggestionRepository suggestionRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private MemoryService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenReturn("tcs_001");
        service = new MemoryService(taskCaseRepository, failureCaseRepository,
                suggestionRepository, outboxRepository, auditService,
                actorResolver, clockProvider, idGenerator, objectMapper);
    }

    private TaskCase createTaskCase(String caseId, String result) {
        TaskCase entity = new TaskCase();
        entity.setCaseId(caseId);
        entity.setMissionId("msn_001");
        entity.setRobotId("rbt_001");
        entity.setSkillId("skl_nav");
        entity.setResult(result);
        entity.setDurationMs(5000L);
        entity.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setTraceId("trace_001");
        return entity;
    }

    private ImprovementSuggestion createSuggestion(String suggestionId, String status) {
        ImprovementSuggestion entity = new ImprovementSuggestion();
        entity.setSuggestionId(suggestionId);
        entity.setCaseId("tcs_001");
        entity.setSuggestionText("Increase timeout");
        entity.setConfidence(0.85);
        entity.setStatus(status);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return entity;
    }

    @Test
    void recordCase_successRecordsTaskCaseOnly() {
        TaskCaseDto result = service.recordCase("msn_001", "rbt_001", "skl_nav",
                "SUCCESS", 5000L, Map.of("zone", "a"), null, "trace_001");

        assertEquals("tcs_001", result.caseId());
        assertEquals("SUCCESS", result.result());
        assertEquals("skl_nav", result.skillId());

        ArgumentCaptor<TaskCase> captor = ArgumentCaptor.forClass(TaskCase.class);
        verify(taskCaseRepository).insert(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getResult());
        verify(failureCaseRepository, never()).insert(any(FailureCase.class));
        verify(suggestionRepository, never()).insert(any(ImprovementSuggestion.class));
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void recordCase_failureCreatesFailureCaseAndSuggestion() {
        when(failureCaseRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        TaskCaseDto result = service.recordCase("msn_001", "rbt_001", "skl_nav",
                "FAILURE", 10000L, Map.of("zone", "b"), "Operation timed out", "trace_001");

        assertEquals("FAILURE", result.result());

        verify(taskCaseRepository).insert(any(TaskCase.class));
        verify(failureCaseRepository).insert(any(FailureCase.class));
        verify(suggestionRepository).insert(any(ImprovementSuggestion.class));
    }

    @Test
    void recordCase_timeoutFailureCategorisesCorrectly() {
        when(failureCaseRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.recordCase("msn_001", "rbt_001", "skl_nav",
                "FAILURE", 10000L, Map.of(), "Operation timed out after 30s", "trace_001");

        ArgumentCaptor<FailureCase> captor = ArgumentCaptor.forClass(FailureCase.class);
        verify(failureCaseRepository).insert(captor.capture());
        assertEquals("TIMEOUT", captor.getValue().getFailureType());
    }

    @Test
    void recordCase_safetyViolationCategorisesCorrectly() {
        when(failureCaseRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.recordCase("msn_001", "rbt_001", "skl_nav",
                "FAILURE", 5000L, Map.of(), "Safety violation detected", "trace_001");

        ArgumentCaptor<FailureCase> captor = ArgumentCaptor.forClass(FailureCase.class);
        verify(failureCaseRepository).insert(captor.capture());
        assertEquals("SAFETY_VIOLATION", captor.getValue().getFailureType());
    }

    @Test
    void recordCase_hardwareFaultCategorisesCorrectly() {
        when(failureCaseRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.recordCase("msn_001", "rbt_001", "skl_nav",
                "FAILURE", 3000L, Map.of(), "Motor sensor fault", "trace_001");

        ArgumentCaptor<FailureCase> captor = ArgumentCaptor.forClass(FailureCase.class);
        verify(failureCaseRepository).insert(captor.capture());
        assertEquals("HARDWARE_FAULT", captor.getValue().getFailureType());
    }

    @Test
    void generateSuggestion_createsPendingSuggestion() {
        ImprovementSuggestionDto result = service.generateSuggestion(
                "tcs_001", "TIMEOUT", "Operation timed out", "skl_nav");

        assertEquals("tcs_001", result.caseId());
        assertEquals("PENDING", result.status());
        assertEquals(0.85, result.confidence(), 0.001);
        assertTrue(result.suggestionText().contains("timeout"));
        verify(suggestionRepository).insert(any(ImprovementSuggestion.class));
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void submitFeedback_pendingSuggestionTransitionsToAccepted() {
        ImprovementSuggestion suggestion = createSuggestion("imp_001", "PENDING");
        when(suggestionRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(suggestion);

        ImprovementSuggestionDto result = service.submitFeedback(
                new FeedbackRequest("imp_001", "Good suggestion, will apply", "ACCEPT"));

        assertEquals("ACCEPTED", result.status());
        assertEquals("Good suggestion, will apply", result.feedback());
        verify(suggestionRepository).updateById(any(ImprovementSuggestion.class));
        verify(auditService).record(any());
    }

    @Test
    void submitFeedback_alreadyAcceptedSuggestionKeepsStatus() {
        ImprovementSuggestion suggestion = createSuggestion("imp_001", "ACCEPTED");
        when(suggestionRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(suggestion);

        ImprovementSuggestionDto result = service.submitFeedback(
                new FeedbackRequest("imp_001", "Already accepted", null));

        assertEquals("ACCEPTED", result.status());
        assertEquals("Already accepted", result.feedback());
    }

    @Test
    void submitFeedback_suggestionNotFoundThrows() {
        when(suggestionRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.submitFeedback(new FeedbackRequest("imp_999", "feedback", "ACCEPT")));
    }

    @Test
    void getCase_existingCaseReturnsDetail() {
        TaskCase taskCase = createTaskCase("tcs_001", "SUCCESS");
        when(taskCaseRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(taskCase);
        when(failureCaseRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MemoryService.CaseDetail result = service.getCase("tcs_001");

        assertEquals("tcs_001", result.taskCase().caseId());
        assertNull(result.failureCase());
    }

    @Test
    void getCase_caseNotFoundThrows() {
        when(taskCaseRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getCase("tcs_999"));
    }

    @Test
    void listCases_returnsPagedCases() {
        TaskCase taskCase = createTaskCase("tcs_001", "SUCCESS");
        Page<TaskCase> page = new Page<>(1, 10);
        page.setRecords(List.of(taskCase));
        page.setTotal(1);
        when(taskCaseRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<TaskCaseDto> result = service.listCases("SUCCESS", null, null, PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals("tcs_001", result.items().get(0).caseId());
    }

    @Test
    void listSuggestions_returnsPagedSuggestions() {
        ImprovementSuggestion suggestion = createSuggestion("imp_001", "PENDING");
        Page<ImprovementSuggestion> page = new Page<>(1, 10);
        page.setRecords(List.of(suggestion));
        page.setTotal(1);
        when(suggestionRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<ImprovementSuggestionDto> result = service.listSuggestions("PENDING", PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals("imp_001", result.items().get(0).suggestionId());
        assertEquals("PENDING", result.items().get(0).status());
    }

    @Test
    void findSimilarCases_returnsMatchingCases() {
        FailureCase failure = new FailureCase();
        failure.setCaseId("tcs_old_001");
        failure.setFailureType("TIMEOUT");
        when(failureCaseRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(failure));
        TaskCase similar = createTaskCase("tcs_old_001", "FAILURE");
        similar.setSkillId("skl_nav");
        when(taskCaseRepository.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(similar));

        List<TaskCaseDto> result = service.findSimilarCases("skl_nav", "TIMEOUT");

        assertEquals(1, result.size());
        assertEquals("tcs_old_001", result.get(0).caseId());
    }

    @Test
    void findSimilarCases_noMatchesReturnsEmpty() {
        when(failureCaseRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<TaskCaseDto> result = service.findSimilarCases("skl_nav", "TIMEOUT");

        assertTrue(result.isEmpty());
    }
}
