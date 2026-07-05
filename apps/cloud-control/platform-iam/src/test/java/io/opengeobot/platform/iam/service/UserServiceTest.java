/*
 * Function: UserService unit tests — list, create and status transition paths
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.iam.service;

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
import io.opengeobot.platform.iam.domain.User;
import io.opengeobot.platform.iam.dto.CreateUserRequest;
import io.opengeobot.platform.iam.dto.UserDto;
import io.opengeobot.platform.iam.repository.OrgRepository;
import io.opengeobot.platform.iam.repository.RoleRepository;
import io.opengeobot.platform.iam.repository.UserOrgRepository;
import io.opengeobot.platform.iam.repository.UserRepository;
import io.opengeobot.platform.iam.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link UserService}. Mocks all repositories and
 * collaborators; verifies paged listing, password hashing on create and
 * status transitions on updateStatus.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserOrgRepository userOrgRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OrgRepository orgRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private PublicIdGenerator idGenerator;

    @Mock
    private ClockProvider clockProvider;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PermissionCache permissionCache;

    @InjectMocks
    private UserService userService;

    @Test
    void listUsers_returnsPagedResult() {
        PageRequest pageRequest = PageRequest.of(1, 10);
        User user = new User();
        user.setUserId("usr_1");
        user.setUsername("alice");
        user.setStatus("ACTIVE");

        Page<User> page = new Page<>(1, 10);
        page.setRecords(List.of(user));
        page.setTotal(1);
        when(userRepository.selectPage(any(Page.class), any())).thenReturn(page);
        when(userRoleRepository.findByUserId("usr_1")).thenReturn(List.of());

        PageResult<UserDto> result = userService.list(pageRequest, null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).userId()).isEqualTo("usr_1");
    }

    @Test
    void createUser_success_hashesPasswordAndSavesUser() {
        CreateUserRequest request = new CreateUserRequest(
                "bob", "Bob", "bob@example.com", "555", "secret", null, List.of());
        when(userRepository.findByUsername("bob")).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("$2a$hashed");
        when(idGenerator.generate("usr")).thenReturn("usr_1");
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());

        User created = new User();
        created.setUserId("usr_1");
        created.setUsername("bob");
        created.setStatus("ACTIVE");
        when(userRepository.findByUserId("usr_1")).thenReturn(created);
        when(userRoleRepository.findByUserId("usr_1")).thenReturn(List.of());

        UserDto result = userService.create(request, "admin_1");

        assertThat(result.userId()).isEqualTo("usr_1");
        assertThat(result.username()).isEqualTo("bob");
        verify(passwordEncoder).encode("secret");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).insert(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo("usr_1");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");

        verify(auditService).record(any(AuditEvent.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void updateUserStatus_success_changesStatus() {
        User user = new User();
        user.setUserId("usr_1");
        user.setStatus("ACTIVE");
        when(userRepository.findByUserId("usr_1")).thenReturn(user);
        when(clockProvider.getClock()).thenReturn(Clock.systemUTC());

        userService.updateStatus("usr_1", "DISABLED", "policy_violation", "admin_1");

        assertThat(user.getStatus()).isEqualTo("DISABLED");
        verify(userRepository).updateById(user);
        verify(auditService).record(any(AuditEvent.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
    }
}
