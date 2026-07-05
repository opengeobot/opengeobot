# M0 Security Report — F-DEPLOY-001

- 功能 ID: F-DEPLOY-001
- 阶段: M0
- 日期: 2026-07-04
- 作者: AxeXie

## 1. TLS / HTTPS

- 当前状态: 开发环境（M0）未强制 TLS，Compose 服务间通信为明文 HTTP。
- 计划: M5 阶段统一启用 TLS 终止（反向代理 + 证书管理），生产部署必须全链路 HTTPS。
- 风险: 仅限本地开发网络，不暴露于公网。

## 2. Authentication

- 当前状态: M0 无认证端点；健康检查 `/actuator/health` 为开放访问，仅返回只读状态。
- 计划: M1 F-PLATFORM-001 实现认证与会话，届时保护所有非健康检查端点。
- 风险: 健康端点不泄露敏感信息，仅返回服务状态与依赖连通性。

## 3. Authorization

- 当前状态: M0 无授权检查；无角色或权限码生效。
- 计划: M1 F-PLATFORM-002 实现用户组织角色权限与数据范围。
- 风险: M0 无业务数据写入端点，仅部署与健康检查。

## 4. Schema Validation

- Flyway 迁移脚本（V1/V2）已通过迁移测试 `FlywayMigrationTest`。
- OpenAPI 契约已定义 `service-health` schema，健康端点响应受契约约束。
- AsyncAPI 事件信封已定义 `event-envelope.yaml`。

## 5. Safety Gateway

- 当前状态: M0 不适用；Safety Gateway 为 M2+ 组件（F-SAFETY-001）。
- 计划: M2 阶段实现边缘 Safety Gateway 与本地急停锁存。

## 6. Docker Images

- 所有 Compose 镜像使用固定版本标签，无 `latest`。
- 镜像版本记录于 `deploy/compose/compose.yml`。

## 7. Secrets

- `.env.example` 仅包含非敏感默认值（端口、数据库名等）。
- `.env` 已在 `.gitignore` 中排除，不会提交至版本控制。
- 生产密钥管理计划于 M5 阶段引入（Vault 或等效方案）。
