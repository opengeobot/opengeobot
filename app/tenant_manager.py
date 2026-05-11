"""
功能：Phase 3 - 多租户管理器
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional

from app.phase3_models import (
    AuditAction,
    AuditLogEntry,
    Permission,
    RolePermission,
    Tenant,
    TenantCreate,
    TenantTier,
    TenantUpdate,
    User,
    UserCreate,
    UserRole,
    UserUpdate,
)


# 角色权限定义
ROLE_PERMISSIONS: Dict[UserRole, List[Permission]] = {
    UserRole.owner: [
        Permission(resource="*", action="*", description="完全访问权限"),
    ],
    UserRole.admin: [
        Permission(resource="project", action="*", description="项目管理"),
        Permission(resource="asset", action="*", description="资产管理"),
        Permission(resource="prompt", action="*", description="提示词管理"),
        Permission(resource="run", action="*", description="运行管理"),
        Permission(resource="insight", action="*", description="洞察查看"),
        Permission(resource="playbook", action="*", description="方案管理"),
        Permission(resource="report", action="*", description="报告查看"),
        Permission(resource="user", action="read", description="用户查看"),
        Permission(resource="user", action="create", description="用户创建"),
        Permission(resource="user", action="update", description="用户更新"),
    ],
    UserRole.editor: [
        Permission(resource="project", action="read", description="项目查看"),
        Permission(resource="asset", action="*", description="资产管理"),
        Permission(resource="prompt", action="*", description="提示词管理"),
        Permission(resource="run", action="create", description="运行创建"),
        Permission(resource="run", action="read", description="运行查看"),
        Permission(resource="insight", action="read", description="洞察查看"),
        Permission(resource="playbook", action="create", description="方案创建"),
        Permission(resource="report", action="read", description="报告查看"),
    ],
    UserRole.viewer: [
        Permission(resource="project", action="read", description="项目查看"),
        Permission(resource="asset", action="read", description="资产查看"),
        Permission(resource="prompt", action="read", description="提示词查看"),
        Permission(resource="run", action="read", description="运行查看"),
        Permission(resource="insight", action="read", description="洞察查看"),
        Permission(resource="report", action="read", description="报告查看"),
    ],
}


class TenantManager:
    """多租户管理器"""
    
    def __init__(self) -> None:
        self._tenants: Dict[str, Tenant] = {}
        self._users: Dict[str, User] = {}
        self._tenant_users: Dict[str, List[str]] = {}  # tenant_id -> [user_ids]
        self._audit_logs: List[AuditLogEntry] = []
    
    # ==================== 租户管理 ====================
    
    def create_tenant(self, payload: TenantCreate) -> Tenant:
        tenant_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        # 根据层级设置限制
        tier_limits = {
            TenantTier.free: {"max_projects": 5, "max_users": 1, "features": []},
            TenantTier.pro: {"max_projects": 50, "max_users": 10, "features": ["advanced_analytics", "scheduled_runs"]},
            TenantTier.enterprise: {"max_projects": 999, "max_users": 999, "features": ["advanced_analytics", "scheduled_runs", "custom_branding", "api_access", "priority_support"]},
        }
        
        limits = tier_limits.get(payload.tier, tier_limits[TenantTier.free])
        
        tenant = Tenant(
            tenant_id=tenant_id,
            tenant_name=payload.tenant_name,
            tier=payload.tier,
            max_projects=limits["max_projects"],
            max_users=limits["max_users"],
            enabled_features=limits["features"],
            created_at=now,
        )
        
        self._tenants[tenant_id] = tenant
        self._tenant_users[tenant_id] = []
        
        return tenant
    
    def get_tenant(self, tenant_id: str) -> Optional[Tenant]:
        tenant = self._tenants.get(tenant_id)
        if tenant and not tenant.deleted:
            return tenant
        return None
    
    def list_tenants(self, include_deleted: bool = False) -> List[Tenant]:
        if include_deleted:
            return list(self._tenants.values())
        return [t for t in self._tenants.values() if not t.deleted]
    
    def update_tenant(self, tenant_id: str, payload: TenantUpdate) -> Optional[Tenant]:
        tenant = self.get_tenant(tenant_id)
        if not tenant:
            return None
        
        if payload.tenant_name is not None:
            tenant.tenant_name = payload.tenant_name
        if payload.tier is not None:
            tenant.tier = payload.tier
        if payload.max_projects is not None:
            tenant.max_projects = payload.max_projects
        if payload.max_users is not None:
            tenant.max_users = payload.max_users
        if payload.enabled_features is not None:
            tenant.enabled_features = payload.enabled_features
        
        tenant.updated_at = datetime.utcnow()
        return tenant
    
    def delete_tenant(self, tenant_id: str) -> bool:
        tenant = self.get_tenant(tenant_id)
        if not tenant:
            return False
        
        tenant.deleted = True
        tenant.updated_at = datetime.utcnow()
        return True
    
    # ==================== 用户管理 ====================
    
    def create_user(self, payload: UserCreate) -> User:
        # 检查租户用户限制
        tenant = self.get_tenant(payload.tenant_id)
        if not tenant:
            raise ValueError("Tenant not found")
        
        current_users = len(self._tenant_users.get(payload.tenant_id, []))
        if current_users >= tenant.max_users:
            raise ValueError(f"Tenant user limit reached ({tenant.max_users})")
        
        user_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        user = User(
            user_id=user_id,
            tenant_id=payload.tenant_id,
            username=payload.username,
            email=payload.email,
            role=payload.role,
            full_name=payload.full_name,
            created_at=now,
        )
        
        self._users[user_id] = user
        if payload.tenant_id not in self._tenant_users:
            self._tenant_users[payload.tenant_id] = []
        self._tenant_users[payload.tenant_id].append(user_id)
        
        return user
    
    def get_user(self, user_id: str) -> Optional[User]:
        user = self._users.get(user_id)
        if user and not user.deleted:
            return user
        return None
    
    def get_user_by_username(self, username: str) -> Optional[User]:
        for user in self._users.values():
            if user.username == username and not user.deleted:
                return user
        return None
    
    def list_users(self, tenant_id: Optional[str] = None, role: Optional[UserRole] = None) -> List[User]:
        if tenant_id:
            user_ids = self._tenant_users.get(tenant_id, [])
            users = [self._users[uid] for uid in user_ids if uid in self._users and not self._users[uid].deleted]
        else:
            users = [u for u in self._users.values() if not u.deleted]
        
        if role:
            users = [u for u in users if u.role == role]
        
        return users
    
    def update_user(self, user_id: str, payload: UserUpdate) -> Optional[User]:
        user = self.get_user(user_id)
        if not user:
            return None
        
        if payload.role is not None:
            user.role = payload.role
        if payload.full_name is not None:
            user.full_name = payload.full_name
        
        return user
    
    def delete_user(self, user_id: str) -> bool:
        user = self.get_user(user_id)
        if not user:
            return False
        
        user.deleted = True
        if user.tenant_id in self._tenant_users:
            self._tenant_users[user.tenant_id].remove(user_id)
        
        return True
    
    # ==================== 权限检查 ====================
    
    def check_permission(self, user_id: str, resource: str, action: str) -> bool:
        user = self.get_user(user_id)
        if not user:
            return False
        
        permissions = ROLE_PERMISSIONS.get(user.role, [])
        
        for perm in permissions:
            if (perm.resource == resource or perm.resource == "*") and (perm.action == action or perm.action == "*"):
                return True
        
        return False
    
    def get_user_permissions(self, user_id: str) -> List[Permission]:
        user = self.get_user(user_id)
        if not user:
            return []
        
        return ROLE_PERMISSIONS.get(user.role, [])
    
    # ==================== 审计日志 ====================
    
    def log_action(
        self,
        tenant_id: str,
        user_id: str,
        action: AuditAction,
        resource: str,
        resource_id: str,
        details: Optional[Dict[str, Any]] = None,
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None,
    ) -> AuditLogEntry:
        log_id = uuid.uuid4().hex
        now = datetime.utcnow()
        
        log_entry = AuditLogEntry(
            log_id=log_id,
            tenant_id=tenant_id,
            user_id=user_id,
            action=action,
            resource=resource,
            resource_id=resource_id,
            details=details or {},
            ip_address=ip_address,
            user_agent=user_agent,
            timestamp=now,
        )
        
        self._audit_logs.append(log_entry)
        return log_entry
    
    def get_audit_logs(
        self,
        tenant_id: Optional[str] = None,
        user_id: Optional[str] = None,
        resource: Optional[str] = None,
        action: Optional[AuditAction] = None,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        limit: int = 1000,
    ) -> List[AuditLogEntry]:
        logs = self._audit_logs[:]
        
        if tenant_id:
            logs = [log for log in logs if log.tenant_id == tenant_id]
        if user_id:
            logs = [log for log in logs if log.user_id == user_id]
        if resource:
            logs = [log for log in logs if log.resource == resource]
        if action:
            logs = [log for log in logs if log.action == action]
        if start_time:
            logs = [log for log in logs if log.timestamp >= start_time]
        if end_time:
            logs = [log for log in logs if log.timestamp <= end_time]
        
        logs.sort(key=lambda x: x.timestamp, reverse=True)
        return logs[:limit]


# 全局租户管理器实例
tenant_manager = TenantManager()
