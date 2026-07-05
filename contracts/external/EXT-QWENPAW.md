<!--
Function: EXT-QWENPAW pinned reference — QwenPaw agent runtime contract
Time: 2026-07-05
Author: AxeXie
-->

# EXT-QWENPAW — QwenPaw Agent Runtime

## Pinned Reference
- Official source: https://qwenpaw.agentscope.io/docs/
- Pin status: PINNED
- Version: M2 simulation (AgentRuntimeProvider adapter, no direct SDK call)

## Contract
- QwenPaw接入仅通过 `AgentRuntimeProvider` 适配接口
- Agent输出为不可信提案，必须经Schema、权限、状态机、资源和安全校验
- 不得直接调用 /cmd_vel、关节、电机或厂商 SDK

## Required By
- F-MCP-001 (MCP Tool registry & invocation)
- F-MISSION-001 (Mission creation & planning)
