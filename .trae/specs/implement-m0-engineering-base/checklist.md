# M0 工程底座验证清单

## 仓库结构
- [x] 目录结构符合 AI 开发约束第 5 节规范（apps/、services/、edge/、contracts/、deploy/、scripts/、tests/、docs/）
- [x] 根 pom.xml 使用 Java 21 + Spring Boot 3.x + MyBatis-Plus，包含 Maven Wrapper
- [x] 前端配置（package.json、pnpm-workspace.yaml、.nvmrc）存在且版本固定
- [x] Python 配置（.python-version）存在且版本固定
- [x] .env.example 列出所有配置项、非敏感示例值、是否必填、用途和默认值
- [x] .gitignore 覆盖 Java/Node/Python/IDE/Docker 产物

## 统一开发脚本
- [x] scripts/dev.sh 存在且可执行，支持 doctor/bootstrap/infra-up/migrate/dev/sim-up/test/e2e/down
- [x] scripts/dev.ps1 存在，与 dev.sh 同语义
- [x] doctor 子命令检查 Git、JDK 21、Docker、Node、Python/uv 版本和端口占用
- [x] bootstrap 子命令安装锁定依赖、初始化本地配置，可重复执行
- [x] down 子命令停止进程和容器但保留数据卷
- [x] 任何删除数据的 reset 操作需单独命令和显式 --confirm

## Docker Compose 部署
- [x] deploy/compose/compose.yml 支持 profiles: infra/observability/cloud/sim/full
- [x] infra profile 包含 PostgreSQL 16 + pgvector、NATS JetStream、MinIO
- [x] 所有服务使用固定镜像版本（无 latest）
- [x] 每个服务配置健康检查（healthcheck）
- [x] 每个服务配置资源上限（resources limits）
- [x] 持久卷配置正确
- [x] 服务启动顺序依赖 readiness，不依赖固定 sleep
- [x] observability profile 包含 vmagent、VictoriaMetrics、Grafana、Vector、Loki

## Java 模块化单体
- [x] apps/cloud-control/pom.xml 聚合模块结构正确
- [x] platform-common 模块包含公共错误模型（ErrorEnvelope、ErrorCode、ProblemDetails）
- [x] platform-common 模块包含 ID 生成器接口和实现
- [x] platform-common 模块包含时间提供者（ClockProvider）
- [x] platform-common 模块包含分页模型
- [x] platform-common 模块包含 Outbox/Inbox 基础设施接口
- [x] platform-iam 模块骨架存在（包结构、Spring Security 配置）
- [x] platform-governance 模块骨架存在（包结构）
- [x] bootstrap 模块包含 Spring Boot 主应用
- [x] /health/live 端点返回 200
- [x] /health/ready 端点在数据库可用时返回 200，不可用时返回 503
- [x] /health/info 端点返回版本信息

## 前端骨架
- [x] apps/web-console 使用 Vue 3 + TypeScript + Vite，strict 模式开启
- [x] 基础布局组件存在（顶栏、左侧导航、主内容区）
- [x] Vue Router 配置存在（路由定义）
- [x] Pinia store 骨架存在（authStore、platformStore）
- [x] i18n 框架配置存在，zh-CN 和 en-US 资源文件非空
- [x] API client 基础存在（axios 实例、拦截器）
- [x] 前端开发服务器可启动并显示基础页面

## 契约目录
- [x] contracts/openapi/ 包含 OpenAPI 3.1 根文件
- [x] contracts/openapi/ 包含健康检查端点契约
- [x] contracts/asyncapi/ 包含 AsyncAPI 根文件骨架
- [x] contracts/mcp/ 包含 MCP Tool Schema 骨架
- [x] contracts/protobuf/ 包含 gRPC proto 骨架
- [x] contracts/skills/ 和 contracts/capabilities/ 包含 JSON Schema 骨架

## 数据库迁移
- [x] Flyway 迁移目录结构存在
- [x] V1 迁移创建所有领域 schema
- [x] V2 迁移创建公共表（outbox_event、inbox_event、sys_operation_audit）
- [x] 公共表包含必要约束（唯一约束、check、索引）
- [x] 空库迁移成功执行
- [x] 迁移不可重复执行（已执行迁移不被修改）

## 服务健康状态
- [x] SM-SERVICE-HEALTH 状态机枚举定义存在（UNKNOWN/STARTING/HEALTHY/DEGRADED/UNHEALTHY/STOPPED）
- [x] service.health_changed.v1 事件 Schema 定义存在
- [x] health-contract 契约定义存在

## 可观测性配置
- [x] Vector 配置存在（日志采集到 Loki）
- [x] vmagent 配置存在（指标采集）
- [x] Grafana 数据源配置存在
- [x] Loki 配置存在

## 测试
- [x] Java 单元测试框架配置存在（JUnit 5 + Testcontainers）
- [x] 健康检查端点集成测试存在
- [x] Flyway 空库迁移测试存在
- [x] 前端测试框架配置存在（Vitest）
- [x] python scripts/validate_platform_manifest.py 验证通过

## 文档
- [x] 根 README.md 存在，包含项目介绍和快速开始
- [x] docs/runbooks/ 包含 M0 工程底座 Runbook（非空）
- [x] docs/test-plans/ 包含 C23 和 C24 测试计划（非空）

## Docker Compose 端到端验证
- [x] `./scripts/dev.sh doctor` 执行成功，无错误
- [x] `./scripts/dev.sh bootstrap` 执行成功
- [x] `./scripts/dev.sh infra-up` 启动基础设施，所有健康检查通过
- [x] `./scripts/dev.sh migrate` 执行迁移成功
- [x] `./scripts/dev.sh dev` 启动应用，健康检查通过
- [x] `./scripts/dev.sh test` 测试通过
- [x] `docker compose --profile full up -d` 完整栈启动成功
- [x] `./scripts/dev.sh down` 停止服务，数据卷保留

## 机器清单
- [x] F-ENGINEERING-001 状态标记为 IN_PROGRESS
- [x] F-DEPLOY-001 状态标记为 IN_PROGRESS
- [x] python scripts/validate_platform_manifest.py 验证通过
