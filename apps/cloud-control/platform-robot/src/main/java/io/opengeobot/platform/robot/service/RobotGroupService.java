/*
 * Function: Robot group service — hierarchical group management (F-ROBOT-002)
 * Time: 2026-07-05
 * Author: AxeXie
 */
package io.opengeobot.platform.robot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opengeobot.platform.common.audit.AuditEvent;
import io.opengeobot.platform.common.audit.AuditService;
import io.opengeobot.platform.common.id.PublicIdGenerator;
import io.opengeobot.platform.common.page.PageRequest;
import io.opengeobot.platform.common.page.PageResult;
import io.opengeobot.platform.common.time.ClockProvider;
import io.opengeobot.platform.robot.domain.RobotGroup;
import io.opengeobot.platform.robot.domain.RobotGroupMember;
import io.opengeobot.platform.robot.dto.CreateRobotGroupRequest;
import io.opengeobot.platform.robot.dto.RobotGroupDto;
import io.opengeobot.platform.robot.dto.UpdateRobotGroupRequest;
import io.opengeobot.platform.robot.repository.RobotGroupMemberRepository;
import io.opengeobot.platform.robot.repository.RobotGroupRepository;
import io.opengeobot.platform.robot.web.ActorResolver;
import io.opengeobot.platform.robot.web.ConflictException;
import io.opengeobot.platform.robot.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Application service for robot group management (F-ROBOT-002). Groups form a
 * hierarchy via {@code parent_id} with a materialised {@code path}. All
 * mutations are recorded in the audit trail.
 */
@Service
public class RobotGroupService {

    private static final Logger log = LoggerFactory.getLogger(RobotGroupService.class);
    private static final String RESOURCE_TYPE = "robot_group";

    private final RobotGroupRepository groupRepository;
    private final RobotGroupMemberRepository memberRepository;
    private final AuditService auditService;
    private final ActorResolver actorResolver;
    private final ClockProvider clockProvider;
    private final PublicIdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    public RobotGroupService(RobotGroupRepository groupRepository,
                             RobotGroupMemberRepository memberRepository,
                             AuditService auditService,
                             ActorResolver actorResolver,
                             ClockProvider clockProvider,
                             PublicIdGenerator idGenerator,
                             ObjectMapper objectMapper) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.auditService = auditService;
        this.actorResolver = actorResolver;
        this.clockProvider = clockProvider;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
    }

    public PageResult<RobotGroupDto> list(PageRequest pageRequest, String parentId) {
        LambdaQueryWrapper<RobotGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(parentId != null && !parentId.isBlank(), RobotGroup::getParentId, parentId)
                .orderByAsc(RobotGroup::getGroupName);
        Page<RobotGroup> page = new Page<>(pageRequest.pageNumber(), pageRequest.pageSize());
        Page<RobotGroup> result = groupRepository.selectPage(page, wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(RobotGroupService::toDto).toList(),
                result.getTotal(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()
        );
    }

    public RobotGroupDto getByGroupId(String groupId) {
        return toDto(requireGroup(groupId));
    }

    @Transactional
    public RobotGroupDto create(CreateRobotGroupRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String groupId = idGenerator.generate("grp");
        String path = "/" + groupId;
        if (request.parentId() != null && !request.parentId().isBlank()) {
            RobotGroup parent = requireGroup(request.parentId());
            path = parent.getPath() + "/" + groupId;
        }
        RobotGroup entity = new RobotGroup();
        entity.setGroupId(groupId);
        entity.setParentId(request.parentId());
        entity.setGroupName(request.groupName());
        entity.setDescription(request.description());
        entity.setPath(path);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        groupRepository.insert(entity);
        audit("robot_group.create", groupId, null, toJson(entity));
        log.info("Created robot group {} ({})", groupId, entity.getGroupName());
        return toDto(entity);
    }

    @Transactional
    public RobotGroupDto update(String groupId, UpdateRobotGroupRequest request) {
        RobotGroup entity = requireGroup(groupId);
        String payloadBefore = toJson(entity);
        if (request.groupName() != null) {
            entity.setGroupName(request.groupName());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        groupRepository.updateById(entity);
        audit("robot_group.update", groupId, payloadBefore, toJson(entity));
        log.info("Updated robot group {}", groupId);
        return toDto(entity);
    }

    @Transactional
    public void delete(String groupId) {
        RobotGroup entity = requireGroup(groupId);
        String payloadBefore = toJson(entity);
        memberRepository.deleteByGroupId(groupId);
        groupRepository.deleteById(entity.getId());
        audit("robot_group.delete", groupId, payloadBefore, null);
        log.info("Deleted robot group {}", groupId);
    }

    @Transactional
    public void addRobot(String groupId, String robotId) {
        requireGroup(groupId);
        if (memberRepository.findByRobotIdAndGroupId(robotId, groupId) != null) {
            throw new ConflictException("Robot '" + robotId + "' is already a member of group '" + groupId + "'");
        }
        RobotGroupMember member = new RobotGroupMember();
        member.setRobotId(robotId);
        member.setGroupId(groupId);
        member.setJoinedAt(OffsetDateTime.now(ZoneOffset.UTC));
        memberRepository.insert(member);
        audit("robot_group.add_robot", groupId, null, toJson(member));
        log.info("Added robot {} to group {}", robotId, groupId);
    }

    @Transactional
    public void removeRobot(String groupId, String robotId) {
        requireGroup(groupId);
        RobotGroupMember member = memberRepository.findByRobotIdAndGroupId(robotId, groupId);
        if (member == null) {
            throw new ResourceNotFoundException(
                    "Robot '" + robotId + "' is not a member of group '" + groupId + "'");
        }
        memberRepository.deleteById(member.getId());
        audit("robot_group.remove_robot", groupId, toJson(member), null);
        log.info("Removed robot {} from group {}", robotId, groupId);
    }

    public List<String> getMembers(String groupId) {
        requireGroup(groupId);
        return memberRepository.findByGroupId(groupId).stream()
                .map(RobotGroupMember::getRobotId)
                .toList();
    }

    // ----- helpers -----

    private RobotGroup requireGroup(String groupId) {
        RobotGroup entity = groupRepository.findByGroupId(groupId);
        if (entity == null) {
            throw new ResourceNotFoundException("Robot group '" + groupId + "' not found");
        }
        return entity;
    }

    private void audit(String action, String resourceId, String payloadBefore, String payloadAfter) {
        AuditEvent event = new AuditEvent(
                "user",
                actorResolver.currentActor(),
                action,
                RESOURCE_TYPE,
                resourceId,
                "SUCCESS",
                null,
                null,
                null,
                actorResolver.currentTraceId(),
                null,
                Instant.now(clockProvider.getClock()),
                payloadBefore,
                payloadAfter
        );
        auditService.record(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise value to JSON for audit", e);
            return null;
        }
    }

    private static RobotGroupDto toDto(RobotGroup entity) {
        return new RobotGroupDto(
                entity.getGroupId(),
                entity.getParentId(),
                entity.getGroupName(),
                entity.getDescription(),
                entity.getPath(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
