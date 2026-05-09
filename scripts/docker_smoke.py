"""
功能：Docker Compose 环境端到端验证
时间：2026-05-08 16:50:00
作者：AxeXie
"""

from __future__ import annotations

import json
import sys
import time
from pathlib import Path
from urllib import request

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def call_api(endpoint, method="GET", body=None, headers=None):
    url = f"http://127.0.0.1:8010{endpoint}"
    headers = headers or {}
    req = request.Request(url, method=method, headers=headers)
    if body:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(body).encode("utf-8")
    else:
        data = None
    try:
        with request.urlopen(req, data=data, timeout=25) as response:
            return json.loads(response.read().decode("utf-8")), response.getheader("X-Trace-Id", "")
    except Exception as exc:
        print(f"API call failed: {exc}")
        raise


def main() -> None:
    print("[1/13] Health check")
    health, trace_id = call_api("/health")
    print(f"  health: {health}, traceId: {trace_id}")

    print("[2/13] Foundation info")
    foundation, _ = call_api("/foundation")
    print(f"  dictionary_version: {foundation.get('dictionary_version')}")
    print(f"  dictionary_enums count: {len(foundation.get('dictionary_enums', []))}")

    print("[3/13] Create project")
    project_body = {
        "project_name": "Docker Demo",
        "project_type": "website",
        "source_url": "https://example.com",
        "brand_name": "DockerDemo",
        "aliases": ["docker demo"],
        "language": "zh-CN",
        "region": "global",
        "competitors": ["comp-x"],
    }
    project, trace_id = call_api("/projects", method="POST", body=project_body)
    project_id = project["project_id"]
    print(f"  project_id: {project_id}, traceId: {trace_id}")

    print("[4/13] Sync assets")
    assets, _ = call_api(f"/projects/{project_id}/assets")
    synced_assets, _ = call_api(f"/projects/{project_id}/assets/sync", method="POST", body={"force": True})
    changes, _ = call_api(f"/projects/{project_id}/asset-changes")
    print(f"  asset_count: {len(assets)} -> {len(synced_assets)}, change_count: {len(changes)}")

    print("[5/13] Check project config (empty)")
    config, trace_id = call_api(f"/projects/{project_id}/config")
    print(f"  config: {config}")

    print("[6/13] Update project config")
    config_body = {"global": {"engine": {"timeoutMs": 5000}}}
    config, trace_id = call_api(f"/projects/{project_id}/config", method="PUT", body={"config": config_body})
    print(f"  updated config: {config}")

    print("[7/13] Get effective config (sanitized)")
    effective, trace_id = call_api(f"/projects/{project_id}/config/effective")
    print(f"  effective keys: {sorted(effective.keys())}")

    print("[8/13] Generate prompts")
    prompts_resp, trace_id = call_api(f"/projects/{project_id}/prompts/generate", method="POST", body={"count": 20})
    print(f"  generated prompts count: {len(prompts_resp) if isinstance(prompts_resp, list) else 1}")

    print("[9/13] Create baseline run")
    run_body = {"run_type": "baseline", "engines": ["engine_alpha", "engine_beta"]}
    baseline, trace_id = call_api(f"/projects/{project_id}/runs", method="POST", body=run_body)
    baseline_run_id = baseline["run_id"]
    print(f"  baseline_run_id: {baseline_run_id}, metrics: {baseline['metrics']}")

    print("[10/13] Generate insights")
    insight_body = {"run_id": baseline_run_id, "limit": 20}
    insights, trace_id = call_api(f"/projects/{project_id}/insights", method="POST", body=insight_body)
    if not insights:
        print("  No insights, creating on-demand run...")
        on_demand_body = {"run_type": "on-demand", "engines": ["engine_alpha", "engine_beta"]}
        on_demand, _ = call_api(f"/projects/{project_id}/runs", method="POST", body=on_demand_body)
        insight_body = {"run_id": on_demand["run_id"], "limit": 20}
        insights, _ = call_api(f"/projects/{project_id}/insights", method="POST", body=insight_body)
    insight_id = insights[0]["insight_id"] if insights else ""
    print(f"  insight_id: {insight_id}, count: {len(insights)}")

    print("[11/13] Generate playbook")
    playbook, trace_id = call_api(
        f"/projects/{project_id}/playbooks",
        method="POST",
        body={"insight_id": insight_id},
    )
    playbook_id = playbook.get("playbook_id")
    print(f"  playbook_id: {playbook_id}, traceId: {trace_id}")

    print("[12/13] Create after run")
    after, trace_id = call_api(f"/projects/{project_id}/runs", method="POST", body={"run_type": "after", "engines": ["engine_alpha", "engine_beta"]})
    after_run_id = after["run_id"]
    print(f"  after_run_id: {after_run_id}, metrics: {after['metrics']}")

    print("[13/13] Verification + monitor + weekly report + audit logs")
    verification, _ = call_api(
        f"/projects/{project_id}/verification",
        method="POST",
        body={"baseline_run_id": baseline_run_id, "after_run_id": after_run_id},
    )
    monitor, _ = call_api(f"/projects/{project_id}/monitor/{after_run_id}?language=zh-CN", method="POST")
    weekly, _ = call_api(f"/projects/{project_id}/weekly-report?language=zh-CN")
    logs, trace_id = call_api(f"/projects/{project_id}/audit-logs?limit=20")
    print(f"  verification_summary: {verification.get('summary')}")
    print(f"  monitor_alert_count: {len(monitor.get('alerts', [])) if isinstance(monitor, dict) else 0}")
    print(f"  weekly_subject: {weekly.get('subject') if isinstance(weekly, dict) else ''}")
    print(f"  audit logs count: {len(logs)}")
    if logs:
        latest = logs[0]
        print(f"  latest: module={latest.get('module')}, event={latest.get('event')}")

    print("\n=== Docker Compose E2E Result ===")
    print("All services are healthy and API verified successfully")


if __name__ == "__main__":
    time.sleep(2)
    main()
