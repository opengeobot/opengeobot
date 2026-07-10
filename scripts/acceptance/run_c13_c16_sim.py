#!/usr/bin/env python3
# Function: C13/C14/C16 sim acceptance skeleton — fleet schedule/conflicts/failovers + adapter compatibility
# Time: 2026-07-10
# Author: AxeXie
"""Run C13 (fleet schedule/conflicts), C14 (failovers), C16 (adapter compatibility).

Endpoints:
  C13: GET /api/v1/fleet/schedule, GET /api/v1/fleet/conflicts
  C14: GET /api/v1/fleet/failovers
  C16: GET /api/v1/adapters/compatibility/{seedModelId}
       seedModelId = mdl_01J00000000000000000000001

Writes reports/acceptance/C13-result.md, C14-result.md, C16-result.md.
HIL for C16 is deferred — see reports/acceptance/C16-adapter-contract-note.md.

Status rules:
  PASS  — all required endpoints for the criterion return HTTP 200
  SKIP  — primary endpoint missing (404)
  FAIL  — login/reachability failure, or other non-200

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

SEED_MODEL_ID = "mdl_01J00000000000000000000001"


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
        f"- **Scope**: sim fleet/adapter skeleton (endpoint reachability)",
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


def check_paths(token: str, paths: list[str]) -> tuple[str, list[str]]:
    """Aggregate status across required paths. 404 on any → SKIP if others OK/404; non-404 fail → FAIL."""
    details: list[str] = []
    saw_ok = False
    saw_skip = False
    saw_fail = False

    for path in paths:
        status, _payload = http_json("GET", path, token=token)
        details.append(f"GET {path} -> {status}")
        if status == 200:
            saw_ok = True
        elif status == 404:
            saw_skip = True
            details.append(f"SKIP detail: {path} not present")
        else:
            saw_fail = True
            details.append(f"FAIL detail: {path} expected 200, got {status}")

    if saw_fail:
        return "FAIL", details
    if saw_ok and not saw_skip:
        return "PASS", details
    if saw_ok and saw_skip:
        # Partial surface: treat as FAIL so missing required sibling is visible
        details.append("FAIL reason: required sibling endpoint returned 404")
        return "FAIL", details
    # all 404
    details.append("SKIP reason: required fleet/adapter endpoints not exposed")
    return "SKIP", details


def run_c13(token: str) -> tuple[str, list[str]]:
    lines = ["criterion: multi-robot fleet schedule + conflicts"]
    status, detail = check_paths(
        token,
        ["/api/v1/fleet/schedule", "/api/v1/fleet/conflicts"],
    )
    lines.extend(detail)
    return status, lines


def run_c14(token: str) -> tuple[str, list[str]]:
    lines = ["criterion: fleet failovers"]
    status, detail = check_paths(token, ["/api/v1/fleet/failovers"])
    lines.extend(detail)
    return status, lines


def run_c16(token: str) -> tuple[str, list[str]]:
    path = f"/api/v1/adapters/compatibility/{SEED_MODEL_ID}"
    lines = [
        "criterion: adapter compatibility (sim contract)",
        f"seed robot_model_id: {SEED_MODEL_ID}",
        "NOTE: HIL (ROS1/Unitree/Custom) deferred to lab — see C16-adapter-contract-note.md",
    ]
    status, detail = check_paths(token, [path])
    lines.extend(detail)
    return status, lines


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

    runners = [
        ("C13", run_c13),
        ("C14", run_c14),
        ("C16", run_c16),
    ]

    for criterion, runner in runners:
        if token is None:
            status = "FAIL"
            lines = [
                f"login as {USERNAME} -> FAIL",
                f"FAIL reason: {login_error}",
            ]
            any_fail = True
        else:
            try:
                lines = [f"login as {USERNAME} -> OK"]
                status, detail = runner(token)
                lines.extend(detail)
                if status == "FAIL":
                    any_fail = True
            except CheckError as exc:
                status = "FAIL"
                lines = [f"login as {USERNAME} -> OK", f"FAIL: {exc}"]
                any_fail = True
            except Exception as exc:  # noqa: BLE001
                status = "FAIL"
                lines = [
                    f"login as {USERNAME} -> OK",
                    f"FAIL: unexpected error: {exc}",
                    traceback.format_exc(),
                ]
                any_fail = True

        write_report(criterion, status, lines)
        print(f"{criterion}: {status} -> {REPORT_DIR / f'{criterion}-result.md'}")

    return 1 if any_fail else 0


if __name__ == "__main__":
    sys.exit(main())
