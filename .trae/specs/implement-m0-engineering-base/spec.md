# M0 工程底座与 Docker Compose 部署验证 Spec

## Why

当前仓库仅有设计文档和机器清单，所有功能均为 `NOT_STARTED`。根据 AI 开发约束第 18 节和实施蓝图第 11/12 节，必须先完成 M0 工程底座（F-ENGINEERING-001 + F-DEPLOY-001），才能进入 M1 平台公共能力开发。M0 退出条件是：新机器按 README 可执行 `doctor -> bootstrap -> infra-up -> migrate -> dev -> test`，并通过 Docker Compose 验证服务健康启动。

## What Changes

### 仓库结构
- 创建规范化目录结构：`apps/`、`services/`、`edge/`、`contracts/`、`deploy/`、`scripts/`、`tests/`、`docs/runbooks/`、`docs/test-plans/`
- 根级配置文件：`pom.xml`、`package.json`、`pnpm-workspace.yaml`、`.python-version`、`.env.example`、`.nvmrc`、`Makefile`

### 统一开发脚本
- 实现 `scripts/dev.sh`（Bash）和 `scripts/dev.ps1`（PowerShell），支持子命令：`doctor`、`bootstrap`、`infra-up`、`migrate`、`dev`、`sim-up`、`test`、`e2e`、`down`
- `doctor`：只读检查工具版本、端口、Docker、配置和必需文件
- `bootstrap`：安装锁定依赖、初始化本地配置
- `infra-up`：启动 PostgreSQL/pgvector、NATS JetStream、MinIO
- `migrate`：执行 Flyway 迁移和种子数据
- `dev`：启动云控制面、前端并输出地址/健康状态
- `test`：运行单元、契约、集成测试
- `down`：停止进程和容器，保留数据

### Java 模块化单体骨架
- 根 `pom.xml`（Java 21 + Spring Boot 3.x + MyBatis-Plus + Maven Wrapper）
- `apps/cloud-control/`：模块化云控制面，包含 `platform-common`、`platform-iam`、`platform-governance` 等领域模块占位
- `platform-common`：公共错误模型、ID 生成、时间、分页、Outbox/Inbox 基础设施、审计基础

### 前端骨架
- `apps/web-console/`：Vue 3 + TypeScript + Vite + Pinia + Vue Router
- 基础布局（顶栏、左侧导航、主内容区）
- i18n 框架（zh-CN、en-US）
- API client 基础

### 契约目录
- `contracts/openapi/`：OpenAPI 3.1 契约骨架
- `contracts/asyncapi/`：AsyncAPI 契约骨架
- `contracts/mcp/`：MCP Tool Schema 骨架
- `contracts/protobuf/`：gRPC proto 骨架

### Docker Compose 部署
- `deploy/compose/compose.yml`，支持 profiles：`infra`、`observability`、`cloud`、`sim`、`full`
- `infra` profile：PostgreSQL + pgvector、NATS JetStream、MinIO（固定镜像版本、健康检查、持久卷）
- `observability` profile：vmagent、VictoriaMetrics、Grafana、Vector、Loki
- `cloud` profile：Java 云控制面、前端
- 每个服务配置健康检查、资源上限、持久卷
- 服务启动顺序依赖 readiness，不依赖固定 sleep

### 数据库迁移
- Flyway 迁移目录结构（按领域 schema 划分）
- 基础 schema 创建迁移：`platform_iam`、`platform_governance`、`robot_registry` 等 schema
- 公共表基础迁移：`outbox_event`、`inbox_event`、`sys_operation_audit`

### 健康检查
- Java 提供 `/health/live`、`/health/ready`、`/health/info`
- Readiness 验证数据库连接等关键依赖
- 服务健康状态机 `SM-SERVICE-HEALTH` 基础实现

### CI 门禁
- 格式化、lint、类型检查
- Java/Python/TypeScript 单元测试
- 契约兼容检查
- Flyway 空库迁移测试
- Docker Compose `full` 健康启动验证

## Impact

- Affected specs: F-ENGINEERING-001、F-DEPLOY-001
- Affected code: 仓库根结构、`apps/cloud-control/`、`apps/web-console/`、`deploy/compose/`、`scripts/`
- 新增目录和文件均按 AI 开发约束第 5 节规范化仓库结构执行
- 不涉及业务逻辑实现，仅建立可运行的工程底座

## ADDED Requirements

### Requirement: 统一开发脚本
系统必须提供 `scripts/dev.sh` 和 `scripts/dev.ps1`，支持 `doctor`、`bootstrap`、`infra-up`、`migrate`、`dev`、`sim-up`、`test`、`e2e`、`down` 子命令。

#### Scenario: doctor 检查
- **WHEN** 新开发者执行 `./scripts/dev.sh doctor`
- **THEN** 脚本只读检查 Git、JDK 21、Docker、Node、Python/uv 版本和端口占用，给出可操作错误

#### Scenario: infra-up 启动基础设施
- **WHEN** 执行 `./scripts/dev.sh infra-up`
- **THEN** Docker Compose 启动 PostgreSQL/pgvector、NATS JetStream、MinIO，所有服务通过健康检查

#### Scenario: migrate 执行迁移
- **WHEN** 执行 `./scripts/dev.sh migrate`
- **THEN** Flyway 执行所有迁移，创建领域 schema 和公共表，种子数据加载成功

#### Scenario: dev 启动应用
- **WHEN** 执行 `./scripts/dev.sh dev`
- **THEN** Java 云控制面和前端启动，输出访问地址和健康状态

#### Scenario: down 停止保留数据
- **WHEN** 执行 `./scripts/dev.sh down`
- **THEN** 停止所有进程和容器，但不删除数据卷

### Requirement: Docker Compose 部署
系统必须提供 `deploy/compose/compose.yml`，支持 `infra`、`observability`、`cloud`、`sim`、`full` profiles。

#### Scenario: infra profile 启动
- **WHEN** 执行 `docker compose --profile infra up -d`
- **THEN** PostgreSQL/pgvector、NATS JetStream、MinIO 启动，健康检查通过

#### Scenario: full profile 启动
- **WHEN** 执行 `docker compose --profile full up -d`
- **THEN** 基础设施 + 可观测栈 + 云控制面 + 前端全部启动，依赖 readiness 排序

### Requirement: Java 模块化单体骨架
系统必须提供基于 Java 21 + Spring Boot 3.x + MyBatis-Plus 的模块化单体骨架。

#### Scenario: 云控制面启动
- **WHEN** Java 云控制面启动
- **THEN** `/health/live` 返回 200，`/health/ready` 在数据库可用时返回 200

#### Scenario: 公共能力可用
- **WHEN** 应用启动后
- **THEN** 错误模型、ID 生成器、时间提供者、Outbox/Inbox 基础设施、审计基础均可注入使用

### Requirement: 前端骨架
系统必须提供 Vue 3 + TypeScript + Vite 前端骨架。

#### Scenario: 前端启动
- **WHEN** 执行前端开发服务器
- **THEN** 页面加载基础布局，i18n 框架工作，zh-CN 和 en-US 资源可用

### Requirement: 数据库迁移
系统必须通过 Flyway 管理数据库 schema，按领域划分。

#### Scenario: 空库迁移
- **WHEN** 对空数据库执行 Flyway migrate
- **THEN** 创建所有领域 schema、公共表（outbox_event、inbox_event、sys_operation_audit）和约束

### Requirement: 可观测性栈
系统必须提供可观测性基础设施配置。

#### Scenario: 可观测栈启动
- **WHEN** 启动 `observability` profile
- **THEN** vmagent、VictoriaMetrics、Grafana、Vector、Loki 启动并健康

## MODIFIED Requirements

### Requirement: 机器清单状态
F-ENGINEERING-001 和 F-DEPLOY-001 的 `implementation_status` 从 `NOT_STARTED` 改为 `IN_PROGRESS`，在全部验证通过后改为 `DONE`。
