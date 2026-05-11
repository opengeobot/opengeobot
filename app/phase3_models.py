"""
功能：Phase 3 - 多租户与权限管理模型
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


# ==================== 租户模型 ====================

class TenantTier(str, Enum):
    free = "free"
    pro = "pro"
    enterprise = "enterprise"


class Tenant(BaseModel):
    tenant_id: str
    tenant_name: str
    tier: TenantTier = TenantTier.free
    max_projects: int = 5
    max_users: int = 1
    enabled_features: List[str] = Field(default_factory=list)
    created_at: datetime
    updated_at: Optional[datetime] = None
    deleted: bool = False


class TenantCreate(BaseModel):
    tenant_name: str
    tier: TenantTier = TenantTier.free


class TenantUpdate(BaseModel):
    tenant_name: Optional[str] = None
    tier: Optional[TenantTier] = None
    max_projects: Optional[int] = None
    max_users: Optional[int] = None
    enabled_features: Optional[List[str]] = None


# ==================== 用户与角色模型 ====================

class UserRole(str, Enum):
    owner = "owner"
    admin = "admin"
    editor = "editor"
    viewer = "viewer"


class User(BaseModel):
    user_id: str
    tenant_id: str
    username: str
    email: str
    role: UserRole = UserRole.viewer
    full_name: str = ""
    created_at: datetime
    last_login: Optional[datetime] = None
    deleted: bool = False


class UserCreate(BaseModel):
    tenant_id: str
    username: str
    email: str
    role: UserRole = UserRole.viewer
    full_name: str = ""


class UserUpdate(BaseModel):
    role: Optional[UserRole] = None
    full_name: Optional[str] = None


# ==================== 权限模型 ====================

class Permission(BaseModel):
    resource: str  # e.g., "project", "asset", "prompt"
    action: str  # e.g., "create", "read", "update", "delete"
    description: str = ""


class RolePermission(BaseModel):
    role: UserRole
    permissions: List[Permission] = Field(default_factory=list)


# ==================== 审计日志模型 ====================

class AuditAction(str, Enum):
    create = "create"
    read = "read"
    update = "update"
    delete = "delete"
    login = "login"
    logout = "logout"
    approve = "approve"
    reject = "reject"


class AuditLogEntry(BaseModel):
    log_id: str
    tenant_id: str
    user_id: str
    action: AuditAction
    resource: str
    resource_id: str
    details: Dict[str, Any] = Field(default_factory=dict)
    ip_address: Optional[str] = None
    user_agent: Optional[str] = None
    timestamp: datetime
