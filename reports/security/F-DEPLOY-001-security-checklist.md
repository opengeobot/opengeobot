# F-DEPLOY-001 Security Checklist

- 功能 ID: F-DEPLOY-001
- 阶段: M0
- 日期: 2026-07-04
- 作者: AxeXie

## Checklist

| 项目 | 状态 | 说明 |
|------|------|------|
| TLS/HTTPS | 不适用 (M0) | 开发环境未强制 TLS，计划 M5 启用 |
| 认证 | 不适用 (M0) | 无认证端点，M1 F-PLATFORM-001 实现 |
| 授权 | 不适用 (M0) | 无授权检查，M1 F-PLATFORM-002 实现 |
| Schema 校验 | 通过 | Flyway 迁移测试通过，OpenAPI 契约已定义 |
| Safety Gateway | 不适用 (M0) | M2+ 组件，F-SAFETY-001 实现 |
| Docker 镜像版本 | 通过 | 全部使用固定版本标签，无 `latest` |
| Secrets 管理 | 通过 | `.env.example` 为非敏感默认值，`.env` 已 gitignore |

## 详细报告

完整安全报告见: `reports/security/m0-security-report.md`
