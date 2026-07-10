#!/usr/bin/env python3
# Function: C03–C12 sim E2E acceptance skeleton — edge/robot/skill/MCP/mission/policy/safety
# Time: 2026-07-10
# Author: AxeXie
"""Run C03–C12 acceptance skeleton checks against cloud-control (sim vertical loop).

Attempts login to localhost:8080 (or OPENGEOBOT_BASE_URL), then GETs list/state
endpoints relevant to each criterion. Writes reports/acceptance/C{xx}-result.md.

Status rules per criterion:
  PASS  — primary endpoint returns HTTP 200
  SKIP  — endpoint missing (404) or not yet exposed
  FAIL  — login/reachability failure, or non-200 (except 404)

Environment variables (optional):
  OPENGEOBOT_BASE_URL   default http://localhost:8080
  OPENGEOBOT_USERNAME   default admin
  OPENGEOBOT_PASSWORD   default admin123
  OPENGEOBOT_HTTP_TIMEOUT  default 15

Exit 0 if every criterion is PASS or SKIP; 1 if any FAIL.
"""

from __future__ import annotations

import json
import os
import sys
import traceback
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
REPORT_DIR = ROOT / "reports" / "acceptance"

BASE_URL = os.environ.get("OPENGEOBOT_BASE_URL", "http://localhost:8080").rstrip("/")
USERNAME = os.environ.get("OPENGEOBOT_USERNAME", "admin")
PASSWORD = os.environ.get("OPENGEOBOT_PASSWORD", "admin123")
TIMEOUT_SEC = float(os.environ.get("OPENGEOBOT_HTTP_TIMEOUT", "15"))

# Criterion → (title, primary GET path, notes)
CHECKS: list[tuple[str, str, str, str]] = [
    ("C03", "Edge gateway registry", "/api/v1/edge-gateways", "list edge gateways"),
    ("C04", "Robot registry", "/api/v1/robots", "list robots"),
    ("C05", "Skill catalog", "/api/v1/skills", "list skills"),
    ("C06", "MCP tools", "/api/v1/mcp/tools", "list MCP tools (mcp-tools surface)"),
    ("C07", "Natural-language missions", "/api/v1/missions", "list missions (NL task surface)"),
    ("C08", "Approval policies", "/api/v1/policies", "list policies (approval gate)"),
    ("C09", "Single-robot execution", "/api/v1/missions", "missions list (execution surface)"),
    ("C10", "Safety modify/block", "/api/v1/safety/events", "safety events (modify/block evidence)"),
    ("C11", "Emergency stop state", "/api/v1/safety/state", "safety state (e-stop latch)"),
    ("C12", "Pause/resume/cancel", "/api/v1/missions", "missions list (lifecycle surface)"),
]


class CheckError(Exception):
    """Single check failure with a human-readable message."""


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def write_report(criterion: str, status: str, lines: list[str]) -> Path:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    name = f"{criterion}-result.md"
    path = REPORT_DIR / name
    body = [
        "<!--",
        f"Function: Acceptance result {name}",
        f"Time: {utc_now()}",
        "Author: AxeXie",
        "-->",
        "",
        f"# {criterion} Acceptance Result",
        "",
        f"- **Status**: `{status}`",
        f"- **Timestamp**: `{utc_now()}`",
        f"- **Base URL**: `{BASE_URL}`",
        f"- **Scope**: sim E2E skeleton (endpoint reachability; full TC-* in test plan)",
        "",
        "## Details",
        "",
    ]
    body.extend(f"- {line}" for line in lines)
    body.append("")
    path.write_text("\n".join(body), encoding="utf-8")
    return path


def http_json(
    method: str,
    path: str,
    *,
    token: str | None = None,
    body: dict[str, Any] | None = None,
) -> tuple[int, Any]:
    url = f"{BASE_URL}{path}"
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SEC) as response:
            raw = response.read().decode("utf-8")
            status = response.getcode()
            payload: Any = json.loads(raw) if raw.strip() else None
            return status, payload
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw) if raw.strip() else None
        except json.JSONDecodeError:
            payload = raw
        return exc.code, payload
    except urllib.error.URLError as exc:
        raise CheckError(f"API not reachable at {BASE_URL}: {exc.reason}") from exc
    except TimeoutError as exc:
        raise CheckError(f"API timeout contacting {BASE_URL}") from exc


def login() -> str:
    status, payload = http_json(
        "POST",
        "/api/v1/auth/login",
        body={"username": USERNAME, "password": PASSWORD},
    )
    if status != 200:
        raise CheckError(f"login returned HTTP {status}")
    if not isinstance(payload, dict):
        raise CheckError("login response is not a JSON object")
    token = payload.get("access_token") or payload.get("accessToken")
    if not token:
        raise CheckError(f"login 200 but missing access_token: keys={list(payload.keys())}")
    return str(token)


def evaluate_endpoint(token: str, path: str, note: str) -> tuple[str, list[str]]:
    """Return (PASS|FAIL|SKIP, detail lines)."""
    status, _payload = http_json("GET", path, token=token)
    if status == 200:
        return "PASS", [f"GET {path} -> 200 ({note})", "sim skeleton endpoint check OK"]
    if status == 404:
        return "SKIP", [
            f"GET {path} -> 404",
            f"SKIP reason: endpoint not present / not yet exposed ({note})",
        ]
    return "FAIL", [
        f"GET {path} -> {status}",
        f"FAIL reason: expected 200 for {note}",
    ]


def main() -> int:
    any_fail = False
    token: str | None = None
    login_error: str | None = None

    try:
        token = login()
    except CheckError as exc:
        login_error = str(exc)
    except Exception as exc:  # noqa: BLE001
        login_error = f"unexpected login error: {exc}\n{traceback.format_exc()}"

    for criterion, title, path, note in CHECKS:
        lines: list[str] = [f"criterion: {title}"]
        if token is None:
            status = "FAIL"
            lines.append(f"login as {USERNAME} -> FAIL")
            lines.append(f"FAIL reason: {login_error}")
            any_fail = True
        else:
            lines.append(f"login as {USERNAME} -> OK")
            try:
                status, detail = evaluate_endpoint(token, path, note)
                lines.extend(detail)
                if status == "FAIL":
                    any_fail = True
            except CheckError as exc:
                status = "FAIL"
                lines.append(f"FAIL: {exc}")
                any_fail = True
            except Exception as exc:  # noqa: BLE001
                status = "FAIL"
                lines.append(f"FAIL: unexpected error: {exc}")
                lines.append(traceback.format_exc())
                any_fail = True

        write_report(criterion, status, lines)
        print(f"{criterion}: {status} -> {REPORT_DIR / f'{criterion}-result.md'}")

    return 1 if any_fail else 0


if __name__ == "__main__":
    sys.exit(main())
