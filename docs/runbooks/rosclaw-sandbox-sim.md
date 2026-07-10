<!--
Function: Runbook — ROSClaw sandbox simulation (parallel to platform sim)
Time: 2026-07-10
Author: AxeXie
-->

# ROSClaw Sandbox 仿真 Runbook

## 目的

在**不改动** OpenGeoBot NATS 主路径的前提下，安装并运行官方 ROSClaw sandbox（无硬件），与 `./scripts/dev.sh sim-up` 并行验证。

## 安全边界

- 平台 **Safety Gateway** 仍为 OpenGeoBot 动作权威；本 sandbox **不**接入平台主路径。
- Agent/LLM **禁止**直连 `/cmd_vel`、关节、电机或厂商 SDK。
- Experimental 能力默认关闭；不得把 sandbox PASS 伪称为 F-ADAPTER / ROSClaw 主路径 DONE。

## 安装（本环境）

官方安装器会从 GitHub 拉取源码。若直连 GitHub 超时，可用镜像克隆后本地安装：

```bash
mkdir -p ~/.rosclaw/{bin,lib}
git clone --depth 1 https://ghproxy.net/https://github.com/ros-claw/rosclaw.git ~/.rosclaw/lib/rosclaw
python3 -m venv ~/.rosclaw/venv
source ~/.rosclaw/venv/bin/activate
pip install -U pip wheel setuptools -i https://pypi.tuna.tsinghua.edu.cn/simple
pip install -e ~/.rosclaw/lib/rosclaw --no-deps -i https://pypi.tuna.tsinghua.edu.cn/simple
# Core sandbox deps (full `pip install -e .` also pulls torch/CUDA via rosclaw-how; optional for sandbox-only)
pip install jieba 'numpy>=1.24' 'mujoco>=3.0' 'mcp>=1.0,<2' 'pyyaml>=6' \
  'fastapi>=0.100' 'uvicorn>=0.23' 'websockets>=12' 'websocket-client>=1' \
  'openai>=1' 'pydantic>=2' 'pymysql>=1.1' 'requests>=2.25' 'semver>=3' \
  'filelock>=3' 'mcap>=1.2' -i https://pypi.tuna.tsinghua.edu.cn/simple
ln -sf ~/.rosclaw/venv/bin/rosclaw ~/.rosclaw/bin/rosclaw
export PATH="$HOME/.rosclaw/bin:$HOME/.rosclaw/venv/bin:$PATH"
```

或官方：

```bash
curl -sSL https://www.rosclaw.io/get | bash
```

## Firstboot / Doctor

```bash
rosclaw firstboot --yes --profile offline --no-telemetry --enable-sandbox
rosclaw doctor
```

## Sandbox 仿真

以官方 CLI 与本机 e-URDF-Zoo 实际 robot id 为准。本仓库验证时 zoo 中为 `ur5e`（`sim_ur5e` 目录不存在）：

```bash
rosclaw sandbox validate ur5e
rosclaw sandbox run --robot ur5e --world tabletop --task reach
```

INSTALL.md 示例中的 `sim_ur5e` 若校验失败，改用 `rosclaw robot list` 中的 id。

## 与平台仿真并行

| 栈 | 入口 | 说明 |
| --- | --- | --- |
| OpenGeoBot | `./scripts/dev.sh sim-up` | edge + safety + sim-adapter（NATS） |
| ROSClaw | `rosclaw sandbox run ...` | 独立 MuJoCo sandbox，不直连平台 |

## 证据

结果写入 `reports/acceptance/rosclaw-sandbox-sim-result.md`。安装/网络失败时记 **FAIL** 与阻塞原因，禁止伪造 PASS。
