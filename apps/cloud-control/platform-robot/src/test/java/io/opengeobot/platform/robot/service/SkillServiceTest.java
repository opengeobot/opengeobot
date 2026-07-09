/*
 * Function: Skill service unit tests — CRUD, publish, disable/enable, versions
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
import io.opengeobot.platform.robot.domain.Skill;
import io.opengeobot.platform.robot.domain.SkillVersion;
import io.opengeobot.platform.robot.dto.CreateSkillRequest;
import io.opengeobot.platform.robot.dto.SkillDto;
import io.opengeobot.platform.robot.dto.SkillVersionDto;
import io.opengeobot.platform.robot.dto.UpdateSkillRequest;
import io.opengeobot.platform.robot.repository.SkillRepository;
import io.opengeobot.platform.robot.repository.SkillVersionRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SkillService}. Covers the SM-SKILL-001 state machine
 * (DRAFT → PUBLISHED → DEPRECATED/DISABLED), version creation on publish,
 * and the disable/enable lifecycle.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillServiceTest {

    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private AuditService auditService;
    @Mock private ActorResolver actorResolver;
    @Mock private ClockProvider clockProvider;
    @Mock private PublicIdGenerator idGenerator;

    private SkillService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        when(actorResolver.currentActor()).thenReturn("user_001");
        when(actorResolver.currentTraceId()).thenReturn("trace_001");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate(any(String.class))).thenReturn("skl_001");
        service = new SkillService(skillRepository, skillVersionRepository,
                outboxRepository, auditService, actorResolver, clockProvider,
                idGenerator, objectMapper);
    }

    private Skill createSkill(String skillId, String name, String status, int version) {
        Skill skill = new Skill();
        skill.setId(1L);
        skill.setSkillId(skillId);
        skill.setName(name);
        skill.setModule("navigation");
        skill.setDescription("Navigation skill");
        skill.setStatus(status);
        skill.setCurrentVersion(version);
        skill.setInputSchema("{}");
        skill.setOutputSchema("{}");
        skill.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        skill.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return skill;
    }

    @Test
    void createSkill_validRequestInsertsDraftSkill() {
        when(skillRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        CreateSkillRequest request = new CreateSkillRequest(
                "nav_skill", "navigation", "desc", "{}", "{}");

        SkillDto result = service.createSkill(request);

        assertEquals("skl_001", result.skillId());
        assertEquals("nav_skill", result.name());
        assertEquals("DRAFT", result.status());
        assertEquals(0, result.currentVersion());

        ArgumentCaptor<Skill> captor = ArgumentCaptor.forClass(Skill.class);
        verify(skillRepository).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getCurrentVersion());
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void createSkill_duplicateNameThrowsConflict() {
        when(skillRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        CreateSkillRequest request = new CreateSkillRequest(
                "nav_skill", "navigation", "desc", "{}", "{}");

        ConflictException ex = assertThrows(ConflictException.class, () -> service.createSkill(request));
        assertTrue(ex.getMessage().contains("nav_skill"));
        verify(skillRepository, never()).insert(any(Skill.class));
    }

    @Test
    void publishSkill_createsVersionAndTransitionsToPublished() {
        Skill skill = createSkill("skl_001", "nav_skill", "DRAFT", 0);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        SkillDto result = service.publishSkill("skl_001", "Initial release");

        assertEquals("PUBLISHED", result.status());
        assertEquals(1, result.currentVersion());

        ArgumentCaptor<SkillVersion> versionCaptor = ArgumentCaptor.forClass(SkillVersion.class);
        verify(skillVersionRepository).insert(versionCaptor.capture());
        assertEquals(1, versionCaptor.getValue().getVersion());
        assertEquals("PUBLISHED", versionCaptor.getValue().getStatus());
        assertEquals("Initial release", versionCaptor.getValue().getChangelog());
        verify(skillRepository).updateById(any(Skill.class));
        verify(outboxRepository).save(any());
        verify(auditService).record(any());
    }

    @Test
    void publishSkill_disabledThrowsConflict() {
        Skill skill = createSkill("skl_001", "nav_skill", "DISABLED", 1);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.publishSkill("skl_001", "Re-publish"));
        assertTrue(ex.getMessage().contains("DISABLED"));
    }

    @Test
    void publishSkill_secondPublishIncrementsVersion() {
        Skill skill = createSkill("skl_001", "nav_skill", "PUBLISHED", 1);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        SkillDto result = service.publishSkill("skl_001", "v2 changes");

        assertEquals(2, result.currentVersion());
        verify(skillVersionRepository).insert(any(SkillVersion.class));
    }

    @Test
    void disableSkill_publishedTransitionsToDisabled() {
        Skill skill = createSkill("skl_001", "nav_skill", "PUBLISHED", 1);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        SkillDto result = service.disableSkill("skl_001");

        assertEquals("DISABLED", result.status());
        verify(skillRepository).updateById(any(Skill.class));
        verify(outboxRepository).save(any());
    }

    @Test
    void disableSkill_alreadyDisabledThrowsConflict() {
        Skill skill = createSkill("skl_001", "nav_skill", "DISABLED", 1);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        assertThrows(ConflictException.class, () -> service.disableSkill("skl_001"));
    }

    @Test
    void enableSkill_disabledWithVersionTransitionsToPublished() {
        Skill skill = createSkill("skl_001", "nav_skill", "DISABLED", 1);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        SkillDto result = service.enableSkill("skl_001");

        assertEquals("PUBLISHED", result.status());
        verify(skillRepository).updateById(any(Skill.class));
    }

    @Test
    void enableSkill_disabledWithoutVersionTransitionsToDraft() {
        Skill skill = createSkill("skl_001", "nav_skill", "DISABLED", 0);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        SkillDto result = service.enableSkill("skl_001");

        assertEquals("DRAFT", result.status());
    }

    @Test
    void enableSkill_notDisabledThrowsConflict() {
        Skill skill = createSkill("skl_001", "nav_skill", "PUBLISHED", 1);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        assertThrows(ConflictException.class, () -> service.enableSkill("skl_001"));
    }

    @Test
    void updateSkill_draftUpdatesFields() {
        Skill skill = createSkill("skl_001", "nav_skill", "DRAFT", 0);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        SkillDto result = service.updateSkill("skl_001",
                new UpdateSkillRequest("Updated desc", "{\"type\":\"object\"}", "{}"));

        assertEquals("Updated desc", result.description());
        assertEquals("{\"type\":\"object\"}", result.inputSchema());
        verify(skillRepository).updateById(any(Skill.class));
        verify(auditService).record(any());
    }

    @Test
    void updateSkill_publishedThrowsConflict() {
        Skill skill = createSkill("skl_001", "nav_skill", "PUBLISHED", 1);
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skill);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.updateSkill("skl_001", new UpdateSkillRequest("new", null, null)));
        assertTrue(ex.getMessage().contains("PUBLISHED"));
    }

    @Test
    void getSkill_notFoundThrowsResourceNotFound() {
        when(skillRepository.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getSkill("skl_999"));
    }

    @Test
    void listVersions_returnsPagedVersions() {
        when(skillRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        Page<SkillVersion> page = new Page<>(1, 10);
        SkillVersion version = new SkillVersion();
        version.setSkillId("skl_001");
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setChangelog("Initial");
        page.setRecords(List.of(version));
        page.setTotal(1);
        when(skillVersionRepository.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<SkillVersionDto> result = service.listVersions("skl_001", PageRequest.of(1, 10));

        assertEquals(1, result.items().size());
        assertEquals(1, result.items().get(0).version());
        assertEquals("PUBLISHED", result.items().get(0).status());
    }

    @Test
    void listVersions_skillNotFoundThrowsResourceNotFound() {
        when(skillRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThrows(ResourceNotFoundException.class,
                () -> service.listVersions("skl_999", PageRequest.of(1, 10)));
    }
}
