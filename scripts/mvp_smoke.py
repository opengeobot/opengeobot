"""
功能：OpenGEO Bot MVP 端到端烟雾验证脚本
时间：2026-05-08 14:00:00
作者：AxeXie
"""

from __future__ import annotations

import json
import os
import socket
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path
from typing import Any, Dict, Iterable, Mapping, MutableMapping, Tuple
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.models import RunType


def _json_dumps(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False).encode("utf-8")


def _find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def _http_json(
    method: str,
    url: str,
    *,
    body: Any | None = None,
    headers: Mapping[str, str] | None = None,
    timeout_s: float = 15,
) -> Tuple[int, Dict[str, str], Any]:
    data = None if body is None else _json_dumps(body)
    req = Request(url=url, method=method.upper(), data=data)
    req.add_header("Accept", "application/json")
    if body is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if headers:
        for key, value in headers.items():
            req.add_header(key, value)

    try:
        with urlopen(req, timeout=timeout_s) as resp:
            resp_headers = {k.lower(): v for k, v in resp.headers.items()}
            raw = resp.read()
            if not raw:
                return resp.status, resp_headers, None
            return resp.status, resp_headers, json.loads(raw.decode("utf-8"))
    except HTTPError as exc:
        raw = exc.read()
        detail = raw.decode("utf-8", errors="replace") if raw else ""
        raise RuntimeError(f"HTTP {exc.code} {method} {url} {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"network error {method} {url} {exc}") from exc


def _wait_for_health(base_url: str, timeout_s: float = 20) -> None:
    deadline = time.time() + timeout_s
    last_error: str | None = None
    while time.time() < deadline:
        try:
            status, headers, body = _http_json("GET", f"{base_url}/health", timeout_s=5)
            if status == 200 and isinstance(body, dict) and body.get("status") == "ok":
                if "x-trace-id" not in headers:
                    raise RuntimeError("missing X-Trace-Id response header")
                return
        except Exception as exc:
            last_error = str(exc)
        time.sleep(0.2)
    raise RuntimeError(f"health check timeout: {last_error or 'unknown'}")


def _start_uvicorn(
    *,
    port: int,
    extra_env: Mapping[str, str],
) -> subprocess.Popen[str]:
    env: MutableMapping[str, str] = dict(os.environ)
    env.update({k: str(v) for k, v in extra_env.items()})
    cmd = [
        sys.executable,
        "-m",
        "uvicorn",
        "app.main:app",
        "--host",
        "127.0.0.1",
        "--port",
        str(port),
        "--log-level",
        "warning",
    ]
    return subprocess.Popen(
        cmd,
        cwd=str(ROOT),
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )


def _stop_process(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=8)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=8)


def _read_process_output(process: subprocess.Popen[str], max_chars: int = 4000) -> str:
    if process.stdout is None:
        return ""
    try:
        return process.stdout.read(max_chars) or ""
    except Exception:
        return ""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def _get_nested(data: Mapping[str, Any], path: Iterable[str]) -> Any:
    current: Any = data
    for part in path:
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def _smoke_backend(storage: str) -> None:
    port = _find_free_port()
    base_url = f"http://127.0.0.1:{port}"
    trace_id = uuid.uuid4().hex
    operator = f"smoke-{storage}"
    headers = {"X-Trace-Id": trace_id, "X-Operator": operator}

    with tempfile.TemporaryDirectory(prefix=f"opengeobot-smoke-{storage}-") as temp_dir:
        config_override_path = str(Path(temp_dir) / "system-config.override.yaml")
        Path(config_override_path).write_text(
            "\n".join(
                [
                    'version: "1.0.0-smoke"',
                    "global:",
                    "  monitoring:",
                    "    citationDropAlertThreshold: 0.99",
                ]
            )
            + "\n",
            encoding="utf-8",
        )
        sqlite_path = str(Path(temp_dir) / "opengeobot-smoke.db")

        proc = _start_uvicorn(
            port=port,
            extra_env={
                "OPEN_GEOBOT_STORAGE": storage,
                "OPEN_GEOBOT_SQLITE_PATH": sqlite_path,
                "OPEN_GEOBOT_CONFIG_PATH": config_override_path,
            },
        )
        try:
            _wait_for_health(base_url)
            status, health_headers, _ = _http_json("GET", f"{base_url}/health", headers=headers)
            _require(status == 200, "health failed")
            _require(health_headers.get("x-trace-id") == trace_id, "traceId not echoed")

            _, _, foundation = _http_json("GET", f"{base_url}/foundation", headers=headers)
            _require(isinstance(foundation, dict), "foundation response invalid")
            _require(isinstance(foundation.get("dictionary_version"), str), "dictionary_version missing")
            _require(isinstance(foundation.get("dictionary_metrics"), list), "dictionary_metrics missing")
            _require(isinstance(foundation.get("dictionary_enums"), list), "dictionary_enums missing")

            _, _, metrics = _http_json("GET", f"{base_url}/dictionary/metrics", headers=headers)
            _require(isinstance(metrics, list), "dictionary metrics invalid")
            _, _, enums = _http_json("GET", f"{base_url}/dictionary/enums", headers=headers)
            _require(isinstance(enums, list), "dictionary enums invalid")

            _, _, project = _http_json(
                "POST",
                f"{base_url}/projects",
                headers=headers,
                body={
                    "project_name": "OpenGEO 烟测",
                    "project_type": "website",
                    "source_url": "https://example.com",
                    "brand_name": "OpenGEO",
                    "aliases": ["opengeo bot"],
                    "language": "zh-CN",
                    "region": "global",
                    "competitors": ["competitor-x"],
                },
            )
            _require(isinstance(project, dict) and isinstance(project.get("project_id"), str), "project create failed")
            project_id = project["project_id"]

            _, _, effective_before = _http_json("GET", f"{base_url}/projects/{project_id}/config/effective", headers=headers)
            _require(isinstance(effective_before, dict), "effective config invalid")
            _require(
                _get_nested(effective_before, ["global", "monitoring", "citationDropAlertThreshold"]) == 0.99,
                "config override not applied",
            )
            _require(
                _get_nested(effective_before, ["global", "monitoring", "errorRateAlertThreshold"]) == 0.05,
                "deep merge missing base key",
            )

            _, _, _ = _http_json(
                "PUT",
                f"{base_url}/projects/{project_id}/config",
                headers=headers,
                body={
                    "config": {
                        "global": {
                            "engine": {
                                "timeoutMs": 123,
                                "apiKey": "should-not-appear",
                            }
                        }
                    }
                },
            )
            _, _, stored_config = _http_json("GET", f"{base_url}/projects/{project_id}/config", headers=headers)
            _require(isinstance(stored_config, dict), "project config query failed")
            stored_payload = stored_config.get("config")
            _require(isinstance(stored_payload, dict), "project config payload invalid")
            _require(_get_nested(stored_payload, ["global", "engine", "timeoutMs"]) == 123, "project config not stored")
            _require(
                _get_nested(stored_payload, ["global", "engine", "apiKey"]) == "should-not-appear",
                "project config sensitive key not stored",
            )
            _, _, effective_after = _http_json("GET", f"{base_url}/projects/{project_id}/config/effective", headers=headers)
            _require(isinstance(effective_after, dict), "effective config invalid")
            _require(
                _get_nested(effective_after, ["global", "monitoring", "citationDropAlertThreshold"]) == 0.99,
                "config override lost after project config merge",
            )
            _require(_get_nested(effective_after, ["global", "engine", "timeoutMs"]) == 123, "project config not applied")
            engine_cfg = _get_nested(effective_after, ["global", "engine"])
            _require(isinstance(engine_cfg, dict) and "apiKey" not in engine_cfg, "sensitive config not removed")

            _, _, _ = _http_json(
                "POST",
                f"{base_url}/projects/{project_id}/prompts/generate",
                headers=headers,
                body={"count": 20},
            )
            _, _, baseline = _http_json(
                "POST",
                f"{base_url}/projects/{project_id}/runs",
                headers=headers,
                body={"run_type": RunType.baseline.value, "engines": ["engine_alpha", "engine_beta"]},
            )
            _require(isinstance(baseline, dict) and isinstance(baseline.get("run_id"), str), "baseline run failed")
            baseline_run_id = baseline["run_id"]

            _, _, insights = _http_json(
                "POST",
                f"{base_url}/projects/{project_id}/insights",
                headers=headers,
                body={"run_id": baseline_run_id, "limit": 20},
            )
            if not isinstance(insights, list) or not insights:
                _, _, retry_run = _http_json(
                    "POST",
                    f"{base_url}/projects/{project_id}/runs",
                    headers=headers,
                    body={"run_type": RunType.on_demand.value, "engines": ["engine_alpha", "engine_beta"]},
                )
                _require(isinstance(retry_run, dict) and isinstance(retry_run.get("run_id"), str), "retry run failed")
                _, _, insights = _http_json(
                    "POST",
                    f"{base_url}/projects/{project_id}/insights",
                    headers=headers,
                    body={"run_id": retry_run["run_id"], "limit": 20},
                )
            _require(isinstance(insights, list) and insights, "insights not generated")

            _, _, playbook = _http_json(
                "POST",
                f"{base_url}/projects/{project_id}/playbooks",
                headers=headers,
                body={"insight_id": insights[0]["insight_id"]},
            )
            _require(isinstance(playbook, dict) and isinstance(playbook.get("playbook_id"), str), "playbook failed")

            _, _, after = _http_json(
                "POST",
                f"{base_url}/projects/{project_id}/runs",
                headers=headers,
                body={"run_type": RunType.after.value, "engines": ["engine_alpha", "engine_beta"]},
            )
            _require(isinstance(after, dict) and isinstance(after.get("run_id"), str), "after run failed")

            _, _, verification = _http_json(
                "POST",
                f"{base_url}/projects/{project_id}/verification",
                headers=headers,
                body={"baseline_run_id": baseline_run_id, "after_run_id": after["run_id"]},
            )
            _require(isinstance(verification, dict) and isinstance(verification.get("report_id"), str), "verification failed")

            _, _, memory = _http_json(
                "POST",
                f"{base_url}/projects/{project_id}/strategy-memory",
                headers=headers,
                body={"playbook_id": playbook["playbook_id"], "verification_report_id": verification["report_id"]},
            )
            _require(isinstance(memory, dict) and isinstance(memory.get("memory_id"), str), "strategy memory failed")

            _, _, monitor = _http_json(
                "POST",
                f"{base_url}/projects/{project_id}/monitor/{after['run_id']}?{urlencode({'language': 'zh-CN'})}",
                headers=headers,
            )
            _require(isinstance(monitor, dict) and isinstance(monitor.get("alerts"), list), "monitor report failed")

            _, _, overview = _http_json("GET", f"{base_url}/projects/{project_id}/overview", headers=headers)
            _require(isinstance(overview, dict), "overview failed")
            _, _, weekly = _http_json(
                "GET",
                f"{base_url}/projects/{project_id}/weekly-report?{urlencode({'language': 'zh-CN'})}",
                headers=headers,
            )
            _require(isinstance(weekly, dict) and isinstance(weekly.get("subject"), str), "weekly report failed")

            _, _, audit_logs = _http_json(
                "GET",
                f"{base_url}/projects/{project_id}/audit-logs?{urlencode({'limit': 50})}",
                headers=headers,
            )
            _require(isinstance(audit_logs, list) and audit_logs, "audit logs missing")
            _require(audit_logs[0].get("traceId") == trace_id, "audit log traceId mismatch")
            _require(audit_logs[0].get("operator") == operator, "audit log operator mismatch")

            timestamps = [item.get("timestamp") for item in audit_logs if isinstance(item, dict)]
            _require(all(isinstance(ts, str) and ts for ts in timestamps), "audit log timestamp invalid")
            _require(timestamps == sorted(timestamps, reverse=True), "audit logs not sorted desc")

            center_ts = timestamps[min(2, len(timestamps) - 1)]
            start_time = center_ts.replace("Z", "")
            end_time = start_time
            _, _, audit_range = _http_json(
                "GET",
                f"{base_url}/projects/{project_id}/audit-logs?{urlencode({'limit': 10, 'start_time': start_time, 'end_time': end_time})}",
                headers=headers,
            )
            _require(isinstance(audit_range, list) and audit_range, "audit logs time range query failed")

            result = {
                "storage": storage,
                "project_id": project_id,
                "baseline_run_id": baseline_run_id,
                "after_run_id": after["run_id"],
                "insight_count": len(insights),
                "playbook_id": playbook["playbook_id"],
                "verification_summary": verification.get("summary"),
                "strategy_memory_id": memory["memory_id"],
                "monitor_alert_count": len(monitor.get("alerts", [])),
                "top_opportunity_count": len(overview.get("top_opportunities", [])),
                "weekly_subject": weekly.get("subject"),
                "audit_log_count": len(audit_logs),
                "dictionary_version": foundation.get("dictionary_version"),
                "trace_id": trace_id,
            }
            print(json.dumps(result, ensure_ascii=False, indent=2))
        finally:
            if proc.poll() is not None and proc.returncode not in (0, None):
                output = _read_process_output(proc)
                _stop_process(proc)
                raise RuntimeError(f"uvicorn exited: {proc.returncode}\n{output}")
            _stop_process(proc)


def main() -> None:
    _smoke_backend("memory")
    _smoke_backend("sqlite")


if __name__ == "__main__":
    main()
