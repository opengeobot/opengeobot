# 设计覆盖审计

审计日期：2026-07-03

## 1. 审计结论

当前仓库已经形成可供 Codex、TRAE 等 AI IDE 执行的三层设计事实源：

1. `AI开发约束与平台公共能力规范 V1.0.md`：工程、安全、技术和质量门禁。
2. `平台功能与数据状态统一实施蓝图 V1.0.md`：前端页面、后端用例、状态机和事务流程。
3. `implementation/platform-feature-manifest.yaml`：机器可核查的端到端功能清单。

设计覆盖已通过机器校验：

```text
28 个功能
42 个页面 ID
107 个后端用例 ID
31 个状态机 ID
C01-C24 全部覆盖
```

这表示“实施所需的规范和追踪范围完整”，不表示平台代码已经实现。清单中 28 个功能当前均为 `NOT_STARTED`，完整平台门禁应当失败，直到存在真实代码和证据。

## 2. 用户目标覆盖

| 用户目标 | 设计证据 | 判定 |
| --- | --- | --- |
| AI IDE 自动读取规范 | 根 `AGENTS.md`、`.trae/rules/project_rules.md` | 已覆盖 |
| 完整环境搭建 | AI 规范第 13/18 节、F-ENGINEERING-001、F-DEPLOY-001 | 已覆盖 |
| 前端完整功能设计 | 实施蓝图第 2-4 节，42 个页面 ID | 已覆盖 |
| 后端业务逻辑统一 | 实施蓝图第 5/7 节，107 个 Command/Query 用例和事务流程 | 已覆盖 |
| 数据与状态统一 | 实施蓝图第 6/8 节，31 个正交状态机及落库规则 | 已覆盖 |
| 用户/角色/权限统一 | F-PLATFORM-001/002、UC-IAM-*、SM-IAM-001 | 已覆盖 |
| 数据字典统一 | F-PLATFORM-003、UC-GOV-001 | 已覆盖 |
| 国际化统一 | F-PLATFORM-003、UC-GOV-002 | 已覆盖 |
| 前后端一致 | 功能清单的页面/用例/表/契约/事件映射 | 已覆盖 |
| 开发、测试、运行闭环 | 统一脚本契约、测试门禁、Definition of Done | 已覆盖 |
| 全平台功能闭环 | 28 功能与 C01-C24 映射、`--require-complete` 门禁 | 设计已覆盖，代码未实施 |

## 3. PDF 章节覆盖

| PDF 章节 | 规范/蓝图落点 | 功能清单 |
| --- | --- | --- |
| 1 项目定位 | AI 规范第 2 节 | 全局 |
| 2 总体架构 | AI 规范第 4 节 | F-DEPLOY/F-EDGE/F-MISSION |
| 3 设计原则 | AI 规范 2.3、8、11 | F-SAFETY/F-EDGE |
| 4 技术选型 | AI 规范第 3 节 | F-ENGINEERING/F-DEPLOY |
| 5.1 用户与指挥端 | 实施蓝图第 2-4 节 | 全部 `P-*` 页面 |
| 5.2 Agent Runtime | AI 规范 7.3、蓝图 5.5 | F-MISSION-001/F-MCP-001 |
| 5.3 Mission Planner | AI 规范 7.4、蓝图 5.5/6.5/7.3 | F-MISSION-* |
| 5.4 Fleet Orchestrator | AI 规范 7.5、蓝图 5.6/6.18/7.10 | F-FLEET-001 |
| 5.5 Robot Registry | AI 规范 7.1、蓝图 5.3/6.4 | F-ROBOT-* |
| 5.6 Skill Registry | AI 规范 7.2、蓝图 5.4/6.8 | F-SKILL-001 |
| 5.7 MCP Tool Registry | AI 规范 7.7、蓝图 5.4/6.10 | F-MCP-001 |
| 5.8 Policy Center | AI 规范 7.6、蓝图 5.7/6.9 | F-POLICY-001 |
| 5.9 Memory Center | AI 规范 7.8、蓝图 5.9/6.16/7.11 | F-MEMORY-001 |
| 5.10 Trace Center | AI 规范 7.8、蓝图 3.6/5.9/6.16 | F-TRACE-001 |
| 6 边缘机器人网关 | AI 规范第 8 节、蓝图 5.8/7.9 | F-EDGE-001/002 |
| 6.2 ROSClaw | AI 规范 3/8、外部集成边界 | F-EDGE-002/F-ADAPTER-001 |
| 6.3 Safety Gateway | AI 规范 8.2/11、蓝图 5.7/7.8 | F-SAFETY-001 |
| 6.4-6.6 适配器 | AI 规范 8.4、蓝图 5.8 | F-ADAPTER-001 |
| 6.7 Local Skill Executor | AI 规范 8.3、蓝图 6.11 | F-EDGE-002 |
| 7 统一能力模型 | AI 规范 7.2、蓝图 3.4/5.4 | F-SKILL/F-ADAPTER |
| 8 通信设计 | AI 规范第 10 节、蓝图第 9 节 | F-EDGE/F-MCP/F-MONITOR |
| 9 核心业务流程 | 蓝图第 7 节 | F-MISSION/F-SAFETY/F-EDGE |
| 10 数据库设计 | AI 规范第 9 节、蓝图第 8 节、清单 `data` | 全部数据功能 |
| 11 接口设计 | AI 规范第 10 节、蓝图第 9 节、清单 `contracts` | 全部接口功能 |
| 12 安全设计 | AI 规范第 11 节、蓝图 6/7.8 | F-SAFETY/F-POLICY |
| 13 部署设计 | AI 规范第 13 节 | F-DEPLOY/F-OPS |
| 14 MVP 路径 | AI 规范第 18 节、蓝图第 11 节 | M0-M6 |
| 15 最终架构 | AI 规范第 3/4 节 | 全局 |
| 16 关键结论 | AI 规范十二条红线 | 全局门禁 |

## 4. 指定技术栈覆盖

| 指定技术 | 规范落点 | 实施功能 |
| --- | --- | --- |
| Java 21 + MyBatis-Plus / Python | AI 规范第 3/14 节 | F-ENGINEERING 和全部后端功能 |
| Vue + WebSocket | AI 规范第 3/14 节、蓝图第 2-4/9 节 | 42 个页面、F-MONITOR |
| QwenPaw | AI 规范 7.3、蓝图 5.5 | F-MISSION-001 |
| MCP 优先，HTTP/gRPC 辅助 | AI 规范 7.7/10 | F-MCP/F-EDGE |
| ROSClaw Edge Runtime | AI 规范第 8 节 | F-EDGE-002 |
| ROS2 主路径 | AI 规范 8.4 | F-ADAPTER-001 |
| ROS1 兼容路径 | AI 规范 8.4 | F-ADAPTER-001 |
| Zenoh 弱网 | AI 规范 10.5、蓝图 7.9 | F-EDGE-002 |
| PostgreSQL/TimescaleDB | AI 规范第 9 节 | 清单全部 `data` 映射 |
| MinIO/S3 | AI 规范 9.5 | F-MEDIA-001 |
| pgvector | AI 规范 9.4 | F-MEMORY-001 |
| NATS + JetStream | AI 规范 10.3 | 全部 `events` |
| VictoriaMetrics/vmagent/Grafana | AI 规范第 12 节 | F-OPS-001 |
| Vector/Loki/PostgreSQL 审计 | AI 规范 6.6/12.3 | F-PLATFORM-004/F-OPS |
| Grafana Alerting/告警服务 | AI 规范 12.4 | F-ALARM-001 |

## 5. 状态一致性审计

已避免以下常见错误：

- Robot 没有单一模糊 `status`，拆分注册、连接、运行和安全四个状态机。
- Edge 拆分身份、连接和运行时状态。
- Mission、Step、Execution 使用独立状态机。
- Approval 不使用一个可复用布尔值，快照变化会失效。
- 发布生命周期和运行灰度状态正交。
- Alarm 主状态与抑制状态正交。
- 急停是锁存安全状态，复位不恢复原 Mission。
- 页面视图状态与后端领域状态分离。
- 当前状态、状态历史、查询投影和遥测事实分离。

机器清单中的 31 个状态机 ID 均能在实施蓝图中找到。

## 6. 当前门禁结果

日常设计校验：

```text
python scripts/validate_platform_manifest.py
PASS: features=28; pages=42; use_cases=107; state_machines=31; C01-C24 covered; NOT_STARTED=28
```

完整实施门禁：

```text
python scripts/validate_platform_manifest.py --require-complete
```

当前应失败 28 项，因为尚无平台实现和证据。该失败是诚实的当前状态，也是防止 AI 仅修改清单状态后宣称完成的必要保护。

## 7. 实施期间必须保持的审计结论

- 新增页面必须绑定功能 ID 和后端用例。
- 新增用例必须绑定数据所有者、权限、契约和状态机。
- 新增状态机必须同步数据库、事件、前端显示和测试。
- 功能标记 `DONE` 时，Schema 要求 `evidence`，校验器检查证据路径真实存在。
- C01-C24 任一失去覆盖，CI 必须失败。
- 真实机器人/HIL 未执行时，涉及功能不得伪造 `HIL_REPORT`。
