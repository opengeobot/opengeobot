# OpenGeoBot 一脑多控平台

OpenGeoBot 是一个面向多机器人、多场景的统一管控平台，采用"一脑多控"架构，通过云端 AI Agent 统一决策、边缘 Safety Gateway 安全执行，实现对异构机器人集群的协同管控。

## 快速开始

### 环境要求

- JDK 21+
- Docker 24+ (with Docker Compose v2)
- Node.js 22+ (推荐使用 nvm)
- pnpm 9+
- Python 3.12+ (with uv)
- Maven 3.9+ (项目已包含 Maven Wrapper)

### 一键启动

```bash
# 1. 检查环境
./scripts/dev.sh doctor

# 2. 安装依赖并初始化配置
./scripts/dev.sh bootstrap

# 3. 启动基础设施 (PostgreSQL, NATS, MinIO)
./scripts/dev.sh infra-up

# 4. 执行数据库迁移
./scripts/dev.sh migrate

# 5. 启动应用 (后端 + 前端)
./scripts/dev.sh dev

# 6. 运行测试
./scripts/dev.sh test

# 7. 停止所有服务 (保留数据)
./scripts/dev.sh down
```

### Docker Compose 完整启动

```bash
# 启动完整栈 (基础设施 + 可观测性 + 云控制面 + 前端)
docker compose -f deploy/compose/compose.yml --profile full up -d

# 查看健康状态
docker compose -f deploy/compose/compose.yml ps

# 停止 (保留数据)
docker compose -f deploy/compose/compose.yml down
```

### 服务地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 云控制面 API | http://localhost:8080 | Java 后端 |
| 前端控制台 | http://localhost:5173 | Vue 3 前端 |
| PostgreSQL | localhost:5432 | 数据库 |
| NATS | localhost:4222 | 消息中间件 |
| MinIO Console | http://localhost:9001 | 对象存储 |
| Grafana | http://localhost:3000 | 可观测面板 |
| VictoriaMetrics | http://localhost:8428 | 指标存储 |
| Loki | http://localhost:3100 | 日志存储 |

### 健康检查

```bash
# Liveness
curl http://localhost:8080/health/live

# Readiness
curl http://localhost:8080/health/ready

# Info
curl http://localhost:8080/health/info
```

## 项目结构

```
opengeobot/
├── apps/
│   ├── cloud-control/        # Java 模块化单体 (Spring Boot 3.x)
│   │   ├── platform-common/  # 公共能力 (错误模型, ID, 时间, 事件, 审计)
│   │   ├── platform-iam/     # 身份与访问管理
│   │   ├── platform-governance/ # 治理与审计
│   │   └── bootstrap/        # 主应用入口
│   └── web-console/          # Vue 3 前端
├── services/
│   ├── agent-runtime/        # Python Agent 运行时
│   └── mcp-tool-gateway/     # MCP 工具网关
├── edge/                     # 边缘运行时
├── contracts/                # 契约 (OpenAPI, AsyncAPI, MCP, protobuf)
├── deploy/
│   ├── compose/              # Docker Compose 配置
│   └── observability/        # 可观测性配置
├── scripts/                  # 统一开发脚本
├── tests/                    # 测试
└── docs/                     # 文档
```

## 技术栈

- **云端管理**: Java 21 + Spring Boot 3.x + MyBatis-Plus
- **前端**: Vue 3 + TypeScript + Vite + Pinia
- **Agent**: Python 3.12 + uv
- **数据库**: PostgreSQL 16 + pgvector
- **消息**: NATS JetStream
- **对象存储**: MinIO
- **可观测**: VictoriaMetrics + Grafana + Loki + Vector

## 开发指南

详见 [docs/runbooks/](docs/runbooks/) 和 [AI开发约束](docs/AI开发约束与平台公共能力规范%20V1.0.md)。

## License

Proprietary - All rights reserved.
