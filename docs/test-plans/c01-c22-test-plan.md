<!--
Function: C01–C22 Platform Closed-Loop Acceptance Test Plan
Time: 2026-07-10
Author: AxeXie
-->

# C01–C22 平台闭环验收测试计划

本计划覆盖 AI 开发约束规范第 17 节验收矩阵中的 **C01–C22**。C23/C24 见 [c23-c24-test-plan.md](./c23-c24-test-plan.md)。

## 通用约定

| 项 | 说明 |
|----|------|
| 状态取值 | `PASS` / `FAIL` / `NOT_APPLICABLE` |
| N/A 限制 | `NOT_APPLICABLE` 必须有批准理由；**C09、C10、C11、C19、C24 不得标记 N/A** |
| 证据目录 | `reports/acceptance/Cxx-result.md`（及同目录附属日志/截图） |
| 默认账号 | `admin` / `admin123`（可用环境变量覆盖） |
| 云控制面 | `http://localhost:8080` |
| 可运行骨架 | `python3 scripts/acceptance/run_c01_c02.py`、`python3 scripts/acceptance/run_c23_c24_check.py` |

### 环境类型

- **sim**：仿真 / 本地 Compose + 云控制面，无真实机器人物理动作。
- **lab**：受控实验室 / HIL，可连接真实或半实物设备；须经 Safety Gateway，禁止绕过 Skill。

---

# C01 — 公共能力

| 项 | 内容 |
|----|------|
| 目标 | 创建用户/组织/角色/权限，授权与撤权实时生效，全部有审计 |
| 关联功能 | `F-PLATFORM-001`、`F-PLATFORM-002`、`F-PLATFORM-004` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C01-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C01-01 | 云控制面已启动；管理员账号可用 | 1. `POST /api/v1/auth/login` 登录<br>2. `GET /api/v1/users`、`/orgs`、`/roles`、`/permissions` | 登录 200 返回 `access_token`；四个列表接口均 200 |
| TC-C01-02 | TC-C01-01 通过；具备 `platform.user.manage` | 1. `POST /api/v1/users` 创建测试用户<br>2. 分配角色 `PUT /users/{id}/roles`<br>3. 用新用户登录验证权限<br>4. 撤权后再次登录/调用受保护接口<br>5. 禁用测试用户清理 | 授权后权限立即生效；撤权后受保护接口 403；全程可审计 |
| TC-C01-03 | 已发生用户/角色变更 | `GET /api/v1/audits?page=1&pageSize=20` | 200；记录含 `action`、`actor_id`、`trace_id`、`result` |
| TC-C01-04 | 组织树已初始化 | 创建子组织并绑定用户；查询组织列表 | 组织 CRUD/列表成功；用户归属正确 |

**状态栏说明**：填写 `PASS`/`FAIL`/`NOT_APPLICABLE`（本项通常不可 N/A）。

**可运行骨架**：`python3 scripts/acceptance/run_c01_c02.py`（覆盖列表与审计 200；可选创建+禁用测试用户）。

---

# C02 — 字典 / i18n

| 项 | 内容 |
|----|------|
| 目标 | 同一业务 code 在中英文正确展示，缓存版本更新，无重复业务字典 |
| 关联功能 | `F-PLATFORM-003` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C02-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C02-01 | 已登录；字典已种子化 | `GET /api/v1/dict/types`；对 `user_status` 等取 items | 200；类型无重复 `type_code`；items 含中英文标签 |
| TC-C02-02 | 已登录 | `GET /api/v1/i18n?locale=zh-CN` 与 `locale=en-US` | 200；若存在同一 `resource_key`，两 locale 均有值且文案不同或至少均非空 |
| TC-C02-03 | 具备字典/i18n 管理权限 | 更新一项字典或 i18n；刷新前端/再次 GET | 新值立即可见；版本/缓存更新可观察（无陈旧重复项） |
| TC-C02-04 | 业务模块已接入公共字典 | 抽查任务/机器人状态展示 | 业务侧不自建同义字典；展示与公共字典一致 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

**可运行骨架**：`python3 scripts/acceptance/run_c01_c02.py`。

---

# C03 — 网关注册

| 项 | 内容 |
|----|------|
| 目标 | 机器证书注册边缘网关，绑定/轮换/撤销可追踪 |
| 关联功能 | `F-EDGE-001` |
| 环境 | sim（证书与注册流程）；lab（真实边缘节点可选） |
| 证据路径 | `reports/acceptance/C03-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C03-01 | 云端与边缘网关可连通 | 使用机器证书注册网关；查询网关详情 | 注册成功；状态投影为已注册/在线；证书指纹可查 |
| TC-C03-02 | 网关已注册 | 执行证书轮换 | 旧证书失效；新证书可用；审计/事件可追踪 |
| TC-C03-03 | 网关已绑定机器人 | 撤销网关证书/解绑 | 后续连接被拒绝；撤销记录含 `trace_id` |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`（未交付边缘时可 N/A 并注明理由）。

---

# C04 — 机器人注册

| 项 | 内容 |
|----|------|
| 目标 | Capability Manifest 校验、机器人上线、状态投影和前端展示一致 |
| 关联功能 | `F-ROBOT-001`、`F-ROBOT-002` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C04-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C04-01 | 网关可用；合法 Manifest | 注册机器人并提交 Capability Manifest | 校验通过；机器人记录创建；能力列表与 Manifest 一致 |
| TC-C04-02 | 非法/缺字段 Manifest | 提交损坏 Manifest | 拒绝注册；错误码稳定；无半成品在线状态 |
| TC-C04-03 | 机器人已注册 | 仿真上线；打开前端机器人详情 | 云端状态、前端展示、边缘上报一致（在线/电量/模式等） |
| TC-C04-04 | 型号/分组已配置 | 绑定型号、分组、维护窗口 | 绑定关系可查；维护态不可被调度执行动作 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C05 — Skill 发布

| 项 | 内容 |
|----|------|
| 目标 | Schema、安全约束、制品签名、仿真证据和版本回滚完整 |
| 关联功能 | `F-SKILL-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C05-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C05-01 | 具备 Skill 发布权限 | 发布含 Schema + 安全约束的 Skill 版本 | 版本号递增；状态进入可发布/已发布；Schema 可检索 |
| TC-C05-02 | 制品签名密钥已配置 | 上传制品并验签 | 签名校验通过才可发布；失败则拒绝 |
| TC-C05-03 | 已有仿真证据附件要求 | 无仿真证据尝试发布高风险 Skill | 被策略拒绝；有证据后可通过 |
| TC-C05-04 | 至少两个已发布版本 | 回滚到上一版本 | 运行时解析到目标版本；审计记录回滚操作 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C06 — MCP 工具

| 项 | 内容 |
|----|------|
| 目标 | QwenPaw 只能看到已授权高层工具，调用映射到应用用例并审计 |
| 关联功能 | `F-MCP-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C06-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C06-01 | MCP Tool Gateway 运行；工具已注册 | 以受限角色列工具 | 仅见已授权高层工具；不见 `/cmd_vel` 等原始控制面 |
| TC-C06-02 | 已授权某工具 | 通过 MCP 调用该工具 | 映射到应用用例；产生审计与 `trace_id` |
| TC-C06-03 | 未授权工具 | 尝试调用 | 拒绝；无物理副作用 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C07 — 自然语言任务

| 项 | 内容 |
|----|------|
| 目标 | 指令 -> Agent 提案 -> DAG -> 可行性/风险校验，非法提案不执行 |
| 关联功能 | `F-MISSION-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C07-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C07-01 | Agent Runtime 可用；机器人在线 | 提交合法自然语言指令 | 生成提案与 DAG；可行性通过；进入待审批或可调度 |
| TC-C07-02 | 同环境 | 提交越权/不可行指令（如无能力 Skill） | 提案被拒绝或不进入执行；说明原因码 |
| TC-C07-03 | 提案含高风险步骤 | 查看风险校验结果 | 风险标记正确；未审批不得执行 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C08 — 审批

| 项 | 内容 |
|----|------|
| 目标 | 普通/高风险任务按策略进入审批，过期或计划变化触发重审 |
| 关联功能 | `F-MISSION-002`、`F-POLICY-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C08-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C08-01 | 策略：普通任务免审/高风险必审 | 分别创建两类任务 | 普通可直达调度；高风险进入审批队列 |
| TC-C08-02 | 待审批任务存在 | 批准 / 驳回 | 状态机合法；驳回不执行；批准后可调度 |
| TC-C08-03 | 审批单未过期策略已配置 | 等待过期或修改计划后再次提交 | 触发重审；旧批准失效 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C09 — 单机执行

| 项 | 内容 |
|----|------|
| 目标 | 调度 -> 下发 -> Safety -> Skill -> 仿真/设备 -> 状态/结果回传 |
| 关联功能 | `F-MISSION-003`、`F-EDGE-002`、`F-ADAPTER-001`、`F-MONITOR-001` |
| 环境 | **sim（必测）**；lab 可选增强 |
| 证据路径 | `reports/acceptance/C09-result.md` |

> **不得标记 `NOT_APPLICABLE`。** 至少在仿真闭环完成全链路。

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C09-01 | 仿真栈 `sim-up`；机器人/Skill 就绪；任务已批准 | 调度单机任务并观察全链路 | 经 Safety Gateway 后执行 Skill；状态回传成功/失败；`trace_id` 贯通 |
| TC-C09-02 | 同 TC-C09-01 | 人为制造 Skill 失败 | 任务失败态正确；无悬挂租约；审计完整 |
| TC-C09-03 | 监控页可用 | 执行中打开实时态势 | 前端投影与边缘上报一致 |

**状态栏说明**：仅允许 `PASS`/`FAIL`。

---

# C10 — 安全修改 / 阻断

| 项 | 内容 |
|----|------|
| 目标 | 超速被修改、禁区/低电量被阻断，云端/边缘/Trace 结果一致 |
| 关联功能 | `F-SAFETY-001`、`F-POLICY-001` |
| 环境 | **sim（必测）**；lab 可选 |
| 证据路径 | `reports/acceptance/C10-result.md` |

> **不得标记 `NOT_APPLICABLE`。**

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C10-01 | Safety 策略含速度上限 | 下发超速参数的动作请求 | 被修改为安全值或拒绝；云端与边缘判定一致；Trace 记录修改原因 |
| TC-C10-02 | 禁区已发布 | 规划进入禁区的动作 | 被阻断；原因码为禁区；无物理越界（仿真位姿不进入） |
| TC-C10-03 | 低电量阈值已配置 | 模拟低电量后下发移动类 Skill | 被阻断或降级；三方（云/边/Trace）结果一致 |

**状态栏说明**：仅允许 `PASS`/`FAIL`。

---

# C11 — 急停

| 项 | 内容 |
|----|------|
| 目标 | 云端、本地、策略三种触发均抢占执行；断网可用、锁存、授权复位 |
| 关联功能 | `F-SAFETY-001` |
| 环境 | **sim（必测）**；lab 验证本地硬件急停 |
| 证据路径 | `reports/acceptance/C11-result.md` |

> **不得标记 `NOT_APPLICABLE`。**

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C11-01 | 任务执行中 | 云端触发急停 | 执行立即抢占停止；状态为 E-STOP；需授权复位 |
| TC-C11-02 | 任务执行中 | 本地/边缘触发急停 | 断网场景下仍生效；云端重连后状态一致 |
| TC-C11-03 | 策略触发条件满足 | 由策略自动急停 | 与手动急停同等锁存；复位需授权；审计完整 |
| TC-C11-04 | 已急停锁存 | 未授权尝试复位 / 授权后复位 | 未授权失败；授权后恢复可调度 |

**状态栏说明**：仅允许 `PASS`/`FAIL`。

---

# C12 — 暂停 / 恢复 / 取消

| 项 | 内容 |
|----|------|
| 目标 | 状态机合法、不可暂停 Skill 安全处理、重复命令无副作用 |
| 关联功能 | `F-MISSION-003`、`F-MONITOR-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C12-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C12-01 | 可暂停 Skill 执行中 | 暂停 → 恢复 | 状态机合法；恢复后继续；无重复副作用 |
| TC-C12-02 | 不可暂停 Skill 执行中 | 下发暂停 | 安全拒绝或安全降级处理；不产生危险中间态 |
| TC-C12-03 | 执行中 | 取消；并重复发送取消 | 进入取消完成；重复命令幂等无副作用 |
| TC-C12-04 | 已结束任务 | 对终态再发暂停/恢复 | 拒绝；错误码稳定 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C13 — 多机调度

| 项 | 内容 |
|----|------|
| 目标 | 能力匹配、评分解释、资源锁、路径冲突和任务分配可验证 |
| 关联功能 | `F-FLEET-001` |
| 环境 | sim（≥2 仿真机器人） |
| 证据路径 | `reports/acceptance/C13-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C13-01 | ≥2 台在线且能力不同 | 提交需特定能力的任务 | 仅匹配具备能力的机器人；评分解释可查 |
| TC-C13-02 | 两任务争用同一资源 | 并发调度 | 资源锁互斥；后到者等待或改派 |
| TC-C13-03 | 路径可能冲突 | 同时下发交叉路径任务 | 冲突检测生效；分配可验证且可解释 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C14 — 故障转移

| 项 | 内容 |
|----|------|
| 目标 | 原执行停止/租约失效后才重分配，物理副作用不重复 |
| 关联功能 | `F-FLEET-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C14-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C14-01 | 任务在机器人 A 执行中 | 使 A 离线/租约过期 | 确认原执行停止后才重分配给 B；无双边同时执行 |
| TC-C14-02 | 已部分完成的幂等步骤 | 故障转移后继续 | 已完成步骤不重复产生物理副作用（仿真可观测） |
| TC-C14-03 | 转移完成 | 查询 Trace | 原执行终止原因与新分配链路完整 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C15 — 弱网 / 离线

| 项 | 内容 |
|----|------|
| 目标 | Zenoh 弱网链路、本地缓存、断点续传、重复/乱序处理和重连补偿 |
| 关联功能 | `F-EDGE-001`、`F-EDGE-002` |
| 环境 | sim（网络损伤）；lab 可选 |
| 证据路径 | `reports/acceptance/C15-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C15-01 | Zenoh/弱网配置启用 | 注入高延迟/丢包后下发任务 | 链路降级可用；关键命令不丢失或可重试 |
| TC-C15-02 | 边缘执行中断网 | 本地缓存继续安全策略内执行或安全停止 | 符合离线策略；重连后状态补偿一致 |
| TC-C15-03 | 重连时注入重复/乱序消息 | 观察去重与乱序处理 | 无重复执行；最终状态收敛正确 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C16 — 多适配器

| 项 | 内容 |
|----|------|
| 目标 | M2 的 ROS2/仿真主路径与 M3 的 ROS1、Unitree、Custom 受控 HIL 使用同一 Capability 契约 |
| 关联功能 | `F-ADAPTER-001`、`F-ADAPTER-002`、`F-ROBOT-002`、`F-EDGE-002` |
| 环境 | sim（ROS2/仿真必测）；lab（ROS1/Unitree/Custom 受控 HIL） |
| 证据路径 | `reports/acceptance/C16-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C16-01 | ROS2 仿真适配器就绪 | 经同一 Capability 执行标准 Skill | 成功；不直连 `/cmd_vel` |
| TC-C16-02 | ROS1 适配器在隔离环境 | 受控 HIL 跑同一契约用例 | 契约兼容；未测设备在报告中明确标记 |
| TC-C16-03 | Unitree/Custom（若在范围） | 受控 HIL | 同契约；失败不得宣称 DONE |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`（未测适配器可单项 N/A，但须列出未测清单；主路径 ROS2/sim 不可空过）。

---

# C17 — 地图 / 区域

| 项 | 内容 |
|----|------|
| 目标 | 坐标系、地图版本、禁区、区域权限和版本变化重评估 |
| 关联功能 | `F-MAP-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C17-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C17-01 | 地图已上传 | 发布地图版本；绑定坐标系 | 版本号正确；前端/规划使用同一版本 |
| TC-C17-02 | 禁区已绘制 | 任务进入禁区 | 被拒绝或重规划；权限不足用户不可改禁区 |
| TC-C17-03 | 执行中发布新地图版本 | 触发重评估 | 进行中任务按策略暂停/重审/安全停止 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C18 — 媒体

| 项 | 内容 |
|----|------|
| 目标 | 拍照/视频资产上传、摘要、权限、任务/Trace 关联和生命周期 |
| 关联功能 | `F-MEDIA-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C18-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C18-01 | MinIO 健康；任务执行中可拍照 | 触发拍照/短视频上传 | 资产入库；含摘要/元数据；关联 `mission_id`/`trace_id` |
| TC-C18-02 | 资产已存在 | 无权限用户访问 | 403；有权限可下载 |
| TC-C18-03 | 生命周期策略配置 | 过期清理或归档 | 对象与元数据一致删除/归档；审计可查 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C19 — Trace 回放

| 项 | 内容 |
|----|------|
| 目标 | 可回答谁、为何、选哪台、执行什么、Safety 如何判定和结果如何 |
| 关联功能 | `F-TRACE-001`、`F-PLATFORM-004` |
| 环境 | **sim（必测）** |
| 证据路径 | `reports/acceptance/C19-result.md` |

> **不得标记 `NOT_APPLICABLE`。**

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C19-01 | 至少完成一次含 Safety 判定的任务 | 打开 Trace 回放；按 `trace_id` 查询 | 可回答：操作者、意图/原因、所选机器人、Skill、Safety 判定、最终结果 |
| TC-C19-02 | 急停或阻断案例存在 | 回放该 Trace | Safety 判定与云端/边缘记录一致 |
| TC-C19-03 | 审计与 Trace 并存 | 交叉核对审计条目 | `trace_id` 可串联任务、审批、工具调用、动作 |

**状态栏说明**：仅允许 `PASS`/`FAIL`。

---

# C20 — Memory 闭环

| 项 | 内容 |
|----|------|
| 目标 | 失败 Trace -> Failure Case -> 候选优化 -> 仿真/审批 -> 灰度/回滚 |
| 关联功能 | `F-MEMORY-001` |
| 环境 | sim |
| 证据路径 | `reports/acceptance/C20-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C20-01 | 存在失败 Trace | 生成 Failure Case | 案例关联原 Trace；可检索 |
| TC-C20-02 | Failure Case 已建 | 生成候选优化并跑仿真 | 仿真证据齐全；自动建议**不能绕过审批** |
| TC-C20-03 | 优化已批准 | 灰度发布后回滚 | 灰度范围可控；回滚后行为恢复；审计完整 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C21 — 监控告警

| 项 | 内容 |
|----|------|
| 目标 | 指标、日志、离线/低电/失败/急停告警、确认/解决和 Runbook |
| 关联功能 | `F-ALARM-001`、`F-OPS-001`、`F-OTA-001`（健康相关） |
| 环境 | sim（observability profile） |
| 证据路径 | `reports/acceptance/C21-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C21-01 | observability 栈已启动 | 查看指标与日志（VM/Loki/Grafana） | 关键服务有指标；日志可按 `trace_id` 检索 |
| TC-C21-02 | 注入离线/低电/任务失败/急停 | 观察告警 | 告警产生；级别正确；含 Runbook 链接或指引 |
| TC-C21-03 | 告警未解决 | 确认 → 解决 | 生命周期合法；重复告警抑制符合策略 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

# C22 — 灾难恢复

| 项 | 内容 |
|----|------|
| 目标 | PostgreSQL、JetStream、MinIO 备份恢复后关键事实和对象可关联 |
| 关联功能 | `F-RECOVERY-001` |
| 环境 | lab / 专用恢复演练环境（勿在生产无确认时执行） |
| 证据路径 | `reports/acceptance/C22-result.md` |

| ID | 前置条件 | 步骤 | 预期 |
|----|----------|------|------|
| TC-C22-01 | 已有基线数据与对象 | 按 Runbook 备份 PostgreSQL / NATS JetStream / MinIO | 备份产物完整；校验和/清单存在 |
| TC-C22-02 | 备份可用 | 在干净环境恢复 | 服务启动；迁移版本一致 |
| TC-C22-03 | 恢复完成 | 抽查任务、媒体对象、`trace_id` 关联 | 关键事实可关联；对象可读取；无 Silent 丢数据 |

**状态栏说明**：`PASS`/`FAIL`/`NOT_APPLICABLE`。

---

## 验收判定汇总

| 编号 | 闭环 | 证据 | 状态（PASS/FAIL/NOT_APPLICABLE） | 备注 |
|------|------|------|----------------------------------|------|
| C01 | 公共能力 | `reports/acceptance/C01-result.md` | | |
| C02 | 字典/i18n | `reports/acceptance/C02-result.md` | | |
| C03 | 网关注册 | `reports/acceptance/C03-result.md` | | |
| C04 | 机器人注册 | `reports/acceptance/C04-result.md` | | |
| C05 | Skill 发布 | `reports/acceptance/C05-result.md` | | |
| C06 | MCP 工具 | `reports/acceptance/C06-result.md` | | |
| C07 | 自然语言任务 | `reports/acceptance/C07-result.md` | | |
| C08 | 审批 | `reports/acceptance/C08-result.md` | | |
| C09 | 单机执行 | `reports/acceptance/C09-result.md` | | **禁止 N/A** |
| C10 | 安全修改/阻断 | `reports/acceptance/C10-result.md` | | **禁止 N/A** |
| C11 | 急停 | `reports/acceptance/C11-result.md` | | **禁止 N/A** |
| C12 | 暂停/恢复/取消 | `reports/acceptance/C12-result.md` | | |
| C13 | 多机调度 | `reports/acceptance/C13-result.md` | | |
| C14 | 故障转移 | `reports/acceptance/C14-result.md` | | |
| C15 | 弱网/离线 | `reports/acceptance/C15-result.md` | | |
| C16 | 多适配器 | `reports/acceptance/C16-result.md` | | |
| C17 | 地图/区域 | `reports/acceptance/C17-result.md` | | |
| C18 | 媒体 | `reports/acceptance/C18-result.md` | | |
| C19 | Trace 回放 | `reports/acceptance/C19-result.md` | | **禁止 N/A** |
| C20 | Memory 闭环 | `reports/acceptance/C20-result.md` | | |
| C21 | 监控告警 | `reports/acceptance/C21-result.md` | | |
| C22 | 灾难恢复 | `reports/acceptance/C22-result.md` | | |

C23/C24 详见 [c23-c24-test-plan.md](./c23-c24-test-plan.md)；结构检查骨架：`python3 scripts/acceptance/run_c23_c24_check.py`（C24 全量安全扫描仍须按该计划与安全报告执行，**C24 不得 N/A**）。

## 关联文档

- [AI 开发约束与平台公共能力规范 V1.0](../AI开发约束与平台公共能力规范%20V1.0.md) §17
- [平台功能与数据状态统一实施蓝图 V1.0](../平台功能与数据状态统一实施蓝图%20V1.0.md)
- [C23/C24 测试计划](./c23-c24-test-plan.md)
- [验收证据说明](../../reports/acceptance/README.md)
