# OpenGEO Bot MVP

<!--
作者：AxeXie
创建时间：2026-05-08 13:58:00
-->

## 功能范围（MVP）

当前实现覆盖 `docs/opengeobot-v1.md` 中 `11.1 MVP 范围` 的最小闭环：

- 网站/仓库项目只读接入
- 提示词导入与初始生成
- 2 个引擎适配器（模拟执行）
- 提及/引用/位置/情感基础解析与指标计算
- 项目概览与 Top 20 机会识别
- Bot 生成 Markdown 草稿
- Baseline 与 After 效果验证
- 每周监控报告与邮件内容结构
- 策略记忆基础记录
- 统一数据字典、统一日志、统一配置与 i18n 集成

## 快速启动

```bash
python -m venv .venv
.venv\\Scripts\\activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

默认存储后端为 SQLite，数据库文件路径：`data/opengeobot.db`。

可通过环境变量切换：

```bash
# 使用 SQLite（默认）
set OPEN_GEOBOT_STORAGE=sqlite
set OPEN_GEOBOT_SQLITE_PATH=data/opengeobot.db

# 切回内存模式（仅临时演示）
set OPEN_GEOBOT_STORAGE=memory
```

## PostgreSQL 持久化（推荐）

```bash
set OPEN_GEOBOT_STORAGE=postgres
set OPEN_GEOBOT_POSTGRES_DSN=postgresql://harness:harness_dev@127.0.0.1:5432/harnessdg
uvicorn app.main:app --host 127.0.0.1 --port 8010
```

> 建议使用 `8010` 端口，避免与本机已有 `8000` 服务冲突。

启动后访问：

- `GET /health`
- `GET /docs`

## 建议调用顺序

1. `POST /projects` 创建项目
2. `POST /projects/{project_id}/prompts/generate` 生成初始提示词
3. `POST /projects/{project_id}/runs` 创建 `baseline` 运行
4. `POST /projects/{project_id}/insights` 生成机会点
5. `POST /projects/{project_id}/playbooks` 生成 Markdown 草稿
6. 再次创建 `after` 运行
7. `POST /projects/{project_id}/verification` 输出效果验证
8. `POST /projects/{project_id}/strategy-memory` 写入策略记忆
9. `GET /projects/{project_id}/overview` 查看项目概览
10. `GET /projects/{project_id}/weekly-report` 查看周报邮件内容

## Logo 资源说明

- 当前设计参考图：`docs/image.png`
- 建议在前端统一引用该风格资源，并扩展三套导出文件：
  - 横版 Logo：`assets/logo/opengeobot-logo-horizontal.png`
  - 方形 App Icon：`assets/logo/opengeobot-icon-square.png`
  - 深色模式 Icon：`assets/logo/opengeobot-icon-dark.png`
