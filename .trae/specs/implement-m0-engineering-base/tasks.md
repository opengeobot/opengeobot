# Tasks

## Task 1: 创建规范化仓库结构和根配置文件
- [x] SubTask 1.1: 创建目录结构（apps/cloud-control、apps/web-console、services/agent-runtime、services/mcp-tool-gateway、edge/、contracts/、deploy/、tests/、docs/runbooks/、docs/test-plans/）
- [x] SubTask 1.2: 创建根 pom.xml（Java 21 + Spring Boot 3.x + MyBatis-Plus 父 POM，Maven Wrapper）
- [x] SubTask 1.3: 创建前端根配置（package.json、pnpm-workspace.yaml、.nvmrc）
- [x] SubTask 1.4: 创建 Python 配置（.python-version、services/agent-runtime/pyproject.toml）
- [x] SubTask 1.5: 创建 .env.example（所有配置项、非敏感示例值、是否必填、用途、默认值）
- [x] SubTask 1.6: 创建 .gitignore（补充 Java/Node/Python/IDE/Docker 忽略项）

## Task 2: 实现统一开发脚本
- [x] SubTask 2.1: 实现 scripts/dev.sh（Bash），支持 doctor/bootstrap/infra-up/migrate/dev/sim-up/test/e2e/down
- [x] SubTask 2.2: 实现 scripts/dev.ps1（PowerShell），与 dev.sh 同语义
- [x] SubTask 2.3: doctor 子命令：检查 Git、JDK 21、Docker、Node、Python/uv 版本和端口占用
- [x] SubTask 2.4: bootstrap 子命令：安装锁定依赖、复制 .env.example 提示
- [x] SubTask 2.5: down 子命令：停止进程和容器，保留数据卷

## Task 3: 创建 Docker Compose 部署配置
- [x] SubTask 3.1: 创建 deploy/compose/compose.yml，支持 profiles: infra/observability/cloud/sim/full
- [x] SubTask 3.2: infra profile：PostgreSQL 16 + pgvector、NATS JetStream、MinIO（固定镜像版本、健康检查、持久卷、资源上限）
- [x] SubTask 3.3: observability profile：vmagent、VictoriaMetrics、Grafana、Vector、Loki（固定镜像版本、健康检查）
- [x] SubTask 3.4: cloud profile：Java 云控制面、前端（依赖 infra readiness）
- [x] SubTask 3.5: 配置服务启动顺序依赖 readiness，不使用固定 sleep
- [x] SubTask 3.6: 创建 deploy/compose/init-scripts/ 目录（PostgreSQL 初始化脚本如需要）

## Task 4: 实现 Java 模块化单体骨架
- [x] SubTask 4.1: apps/cloud-control/pom.xml 聚合模块
- [x] SubTask 4.2: apps/cloud-control/platform-common 模块：公共错误模型（ErrorEnvelope、ErrorCode、ProblemDetails）、ID 生成器（PublicIdGenerator）、时间提供者（ClockProvider）、分页模型、Outbox/Inbox 基础设施接口
- [x] SubTask 4.3: apps/cloud-control/platform-iam 模块骨架（Spring Security 配置、包结构，无业务逻辑）
- [x] SubTask 4.4: apps/cloud-control/platform-governance 模块骨架（包结构，无业务逻辑）
- [x] SubTask 4.5: apps/cloud-control/bootstrap 模块：Spring Boot 主应用、健康检查端点（/health/live、/health/ready、/health/info）、数据库连接配置
- [x] SubTask 4.6: 健康检查实现：liveness 返回 200，readiness 验证数据库连接

## Task 5: 创建前端骨架
- [x] SubTask 5.1: apps/web-console Vite + Vue 3 + TypeScript 项目初始化（strict 模式）
- [x] SubTask 5.2: 基础布局组件（顶栏、左侧导航、主内容区、断网提示）
- [x] SubTask 5.3: Vue Router 路由配置（登录页、工作台占位页）
- [x] SubTask 5.4: Pinia store 骨架（authStore、platformStore）
- [x] SubTask 5.5: i18n 框架配置（vue-i18n，zh-CN/en-US 资源文件）
- [x] SubTask 5.6: API client 基础（axios 实例、拦截器、错误处理）

## Task 6: 创建契约目录骨架
- [x] SubTask 6.1: contracts/openapi/ OpenAPI 3.1 根文件和健康检查端点契约
- [x] SubTask 6.2: contracts/asyncapi/ AsyncAPI 根文件骨架
- [x] SubTask 6.3: contracts/mcp/ MCP Tool Schema 骨架
- [x] SubTask 6.4: contracts/protobuf/ gRPC proto 骨架（edge health check）
- [x] SubTask 6.5: contracts/skills/ 和 contracts/capabilities/ JSON Schema 骨架

## Task 7: 实现数据库迁移
- [x] SubTask 7.1: Flyway 迁移目录结构（apps/cloud-control/bootstrap/src/main/resources/db/migration/）
- [x] SubTask 7.2: V1 迁移：创建领域 schema（platform_iam、platform_governance、robot_registry、skill_registry、mission、fleet、policy、trace、memory、map_scene、media、ops）
- [x] SubTask 7.3: V2 迁移：创建公共表（outbox_event、inbox_event、sys_operation_audit）及约束和索引
- [x] SubTask 7.4: 验证空库迁移成功

## Task 8: 实现服务健康状态和事件
- [x] SubTask 8.1: SM-SERVICE-HEALTH 状态机枚举定义（UNKNOWN/STARTING/HEALTHY/DEGRADED/UNHEALTHY/STOPPED）
- [x] SubTask 8.2: service.health_changed.v1 事件 Schema 定义
- [x] SubTask 8.3: 健康检查配置契约（health-contract）

## Task 9: 创建可观测性配置
- [x] SubTask 9.1: deploy/observability/ Vector 配置（日志采集到 Loki）
- [x] SubTask 9.2: deploy/observability/ vmagent 配置（指标采集）
- [x] SubTask 9.3: deploy/observability/ Grafana 数据源和基础仪表盘配置
- [x] SubTask 9.4: deploy/observability/ Loki 配置

## Task 10: 实现测试框架和验证
- [x] SubTask 10.1: Java 单元测试框架（JUnit 5 + Testcontainers 依赖配置）
- [x] SubTask 10.2: 健康检查端点集成测试
- [x] SubTask 10.3: Flyway 空库迁移测试
- [x] SubTask 10.4: 前端基础组件测试框架（Vitest）
- [x] SubTask 10.5: scripts/validate_platform_manifest.py 验证通过

## Task 11: 创建文档和 Runbook
- [x] SubTask 11.1: 根 README.md（项目介绍、快速开始、统一脚本说明）
- [x] SubTask 11.2: docs/runbooks/ M0 工程底座 Runbook（环境准备、启动、健康检查、故障排查）
- [x] SubTask 11.3: docs/test-plans/ C23 和 C24 测试计划

## Task 12: Docker Compose 端到端验证
- [x] SubTask 12.1: 执行 `./scripts/dev.sh doctor` 验证环境检查
- [x] SubTask 12.2: 执行 `./scripts/dev.sh bootstrap` 验证依赖安装
- [x] SubTask 12.3: 执行 `./scripts/dev.sh infra-up` 验证基础设施启动和健康
- [x] SubTask 12.4: 执行 `./scripts/dev.sh migrate` 验证数据库迁移
- [x] SubTask 12.5: 执行 `./scripts/dev.sh dev` 验证应用启动和健康检查
- [x] SubTask 12.6: 执行 `./scripts/dev.sh test` 验证测试通过
- [x] SubTask 12.7: 执行 `docker compose --profile full up -d` 验证完整栈启动

## Task 13: 更新机器清单和证据
- [x] SubTask 13.1: 更新 platform-feature-manifest.yaml 中 F-ENGINEERING-001 状态为 IN_PROGRESS
- [x] SubTask 13.2: 更新 platform-feature-manifest.yaml 中 F-DEPLOY-001 状态为 IN_PROGRESS
- [x] SubTask 13.3: 添加 evidence 路径引用（contracts、backend、test_reports、runbook、deployment）
- [x] SubTask 13.4: 验证 `python scripts/validate_platform_manifest.py` 通过

# Task Dependencies
- [Task 2] depends on [Task 1]
- [Task 3] depends on [Task 1]
- [Task 4] depends on [Task 1]
- [Task 5] depends on [Task 1]
- [Task 7] depends on [Task 4]
- [Task 8] depends on [Task 4]
- [Task 9] depends on [Task 3]
- [Task 10] depends on [Task 4, Task 5, Task 7]
- [Task 11] depends on [Task 2, Task 3]
- [Task 12] depends on [Task 2, Task 3, Task 4, Task 5, Task 7, Task 9]
- [Task 13] depends on [Task 12]
- [Task 1, Task 6] 无依赖，可并行启动
