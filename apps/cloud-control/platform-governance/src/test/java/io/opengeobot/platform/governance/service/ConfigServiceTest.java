/*
 * Function: ConfigService unit tests — list, create and update paths for platform configs
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
import io.opengeobot.platform.governance.config.repository.ConfigHistoryRepository;
import io.opengeobot.platform.governance.config.repository.ConfigRepository;
import io.opengeobot.platform.governance.domain.config.ConfigHistory;
import io.opengeobot.platform.governance.domain.config.SysConfig;
import io.opengeobot.platform.governance.dto.ConfigDto;
import io.opengeobot.platform.governance.dto.CreateConfigRequest;
import io.opengeobot.platform.governance.dto.UpdateConfigRequest;
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
 * Pure unit tests for {@link ConfigService}. Verifies paged listing, creation
 * of a new versioned config (with history + outbox event), and an update that
 * increments the version and appends history.
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ConfigHistoryRepository configHistoryRepository;

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
    private ConfigService configService;

    @Test
    void listConfigs_returnsPagedResult() {
        PageRequest pageRequest = PageRequest.of(1, 10);
        SysConfig config = new SysConfig();
        config.setConfigKey("site.title");
        config.setConfigValue("OpenGeoBot");
        config.setValueType("string");
        config.setModule("web");
        config.setEncrypted(false);
        config.setVersion(1);

        Page<SysConfig> page = new Page<>(1, 10);
        page.setRecords(List.of(config));
        page.setTotal(1);
        when(configRepository.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<ConfigDto> result = configService.listConfigs(null, null, pageRequest);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).configKey()).isEqualTo("site.title");
        assertThat(result.items().get(0).configValue()).isEqualTo("OpenGeoBot");
    }

    @Test
    void createConfig_success_returnsDtoWithHistoryAndEvent() {
        CreateConfigRequest request = new CreateConfigRequest(
                "site.title", "OpenGeoBot", "string", "web", "desc", false);
        when(configRepository.selectCount(any())).thenReturn(0L);
        when(actorResolver.currentActor()).thenReturn("admin_1");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate("evt")).thenReturn("evt_01H");

        ConfigDto result = configService.createConfig(request);

        assertThat(result.configKey()).isEqualTo("site.title");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.encrypted()).isFalse();
        verify(configRepository).insert(any(SysConfig.class));
        verify(configHistoryRepository).insert(any(ConfigHistory.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    void updateConfig_success_incrementsVersionAndAppendsHistory() {
        SysConfig entity = new SysConfig();
        entity.setConfigKey("site.title");
        entity.setConfigValue("old");
        entity.setValueType("string");
        entity.setModule("web");
        entity.setEncrypted(false);
        entity.setVersion(1);
        entity.setStatus("ACTIVE");
        when(configRepository.selectOne(any())).thenReturn(entity);
        when(actorResolver.currentActor()).thenReturn("admin_1");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());
        when(idGenerator.generate("evt")).thenReturn("evt_02H");

        ConfigDto result = configService.updateConfig(
                "site.title", new UpdateConfigRequest("new value", "new desc"));

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.configValue()).isEqualTo("new value");
        verify(configRepository).updateById(entity);
        verify(configHistoryRepository).insert(any(ConfigHistory.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
        verify(auditService).record(any(AuditEvent.class));
    }
}
