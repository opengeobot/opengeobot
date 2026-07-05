/*
 * Function: DictService unit tests — list, create and publish paths for dict types
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.governance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.event.OutboxEvent;
import io.opengeobot.platform.common.event.OutboxRepository;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.governance.dict.repository.DictItemRepository;
import io.opengeobot.platform.governance.dict.repository.DictTypeRepository;
import io.opengeobot.platform.governance.domain.dict.DictType;
import io.opengeobot.platform.governance.dto.CreateDictTypeRequest;
import io.opengeobot.platform.governance.dto.DictTypeDto;
import io.opengeobot.platform.governance.web.ActorResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link DictService}. Verifies paged listing of dict
 * types, creation of a new DRAFT type, and the DRAFT → PUBLISHED transition
 * which emits a dictionary-changed outbox event.
 */
@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @Mock
    private DictTypeRepository dictTypeRepository;

    @Mock
    private DictItemRepository dictItemRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ActorResolver actorResolver;

    @Mock
    private ClockProvider clockProvider;

    @Mock
    private PublicIdGenerator idGenerator;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DictService dictService;

    @Test
    void listTypes_returnsPagedResult() {
        PageRequest pageRequest = PageRequest.of(1, 10);
        DictType type = new DictType();
        type.setTypeCode("robot_type");
        type.setTypeName("Robot Type");
        type.setStatus("PUBLISHED");
        type.setVersion(1);

        Page<DictType> page = new Page<>(1, 10);
        page.setRecords(List.of(type));
        page.setTotal(1);
        when(dictTypeRepository.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<DictTypeDto> result = dictService.listTypes(pageRequest, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).typeCode()).isEqualTo("robot_type");
    }

    @Test
    void createType_success_returnsDraftDto() {
        CreateDictTypeRequest request = new CreateDictTypeRequest("robot_type", "Robot Type", "desc");
        // existsByTypeCode -> selectCount returns 0 (no conflict)
        when(dictTypeRepository.selectCount(any())).thenReturn(0L);
        when(actorResolver.currentActor()).thenReturn("admin_1");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());

        DictTypeDto result = dictService.createType(request);

        assertThat(result.typeCode()).isEqualTo("robot_type");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.version()).isEqualTo(1);
        verify(dictTypeRepository).insert(any(DictType.class));
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    void publishType_success_setsPublishedStatusAndEmitsEvent() {
        DictType entity = new DictType();
        entity.setTypeCode("robot_type");
        entity.setStatus("DRAFT");
        entity.setVersion(1);
        when(dictTypeRepository.selectOne(any())).thenReturn(entity);
        when(actorResolver.currentActor()).thenReturn("admin_1");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate("evt")).thenReturn("evt_01H");

        DictTypeDto result = dictService.publishType("robot_type");

        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.publishedVersion()).isEqualTo(1);
        verify(dictTypeRepository).updateById(entity);
        verify(outboxRepository).save(any(OutboxEvent.class));
        verify(auditService).record(any(AuditEvent.class));
    }
}
