"""
功能：分页支持模块
时间：2026-05-10
作者：AxeXie
"""

from __future__ import annotations

from typing import Any, Dict, Generic, List, TypeVar, Tuple

from pydantic import BaseModel, Field

T = TypeVar("T")


class PaginatedResponse(BaseModel, Generic[T]):
    """分页响应模型"""
    items: List[T]
    total: int
    page: int
    page_size: int
    has_next: bool
    has_prev: bool
    
    @property
    def total_pages(self) -> int:
        if self.page_size <= 0:
            return 0
        return (self.total + self.page_size - 1) // self.page_size


class PaginationParams(BaseModel):
    """分页查询参数"""
    page: int = Field(default=1, ge=1, description="页码(从1开始)")
    page_size: int = Field(default=20, ge=1, le=100, description="每页数量(1-100)")


def apply_pagination(items: List[T], page: int, page_size: int) -> Tuple[List[T], int]:
    """
    应用分页到列表
    返回: (分页后的items, 总数)
    """
    total = len(items)
    start = (page - 1) * page_size
    end = start + page_size
    paginated_items = items[start:end]
    return paginated_items, total


def build_paginated_response(
    items: List[T],
    total: int,
    page: int,
    page_size: int
) -> Dict[str, Any]:
    """
    构建分页响应字典
    """
    has_next = (page * page_size) < total
    has_prev = page > 1
    
    return {
        "items": items,
        "total": total,
        "page": page,
        "page_size": page_size,
        "has_next": has_next,
        "has_prev": has_prev,
    }
