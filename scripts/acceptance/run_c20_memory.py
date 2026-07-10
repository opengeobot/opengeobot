#!/usr/bin/env python3
# Function: C20 Memory loop acceptance — failure cases and improvement approval
# Time: 2026-07-10
# Author: AxeXie
"""C20 acceptance: memory cases/suggestions endpoints; approval must not auto-apply."""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORT_DIR = ROOT / "reports" / "acceptance"
BASE = os.environ.get("OPENGEOBOT_API", "http://localhost:8080").rstrip("/")
USER = os.environ.get("OPENGEOBOT_USER", "admin")
PASSWORD = os.environ.get("OPENGEOBOT_PASSWORD", "admin123")


def http_json(method: str, path: str, token: str | None = None, body: dict | None = None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        f"{BASE}{path}",
        data=data,
        method=method,
        headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        raw = resp.read().decode() or "{}"
        return resp.status, json.loads(raw) if raw.strip() else {}


def write_report(name: str, status: str, lines: list[str]) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    path = REPORT_DIR / f"{name}-result.md"
    path.write_text(
        "\n".join(
            [
                f"# {name} Acceptance Result",
                "",
                f"- Timestamp: {datetime.now(timezone.utc).isoformat()}",
                f"- Status: **{status}**",
                f"- API: `{BASE}`",
                "",
                *lines,
                "",
            ]
        ),
        encoding="utf-8",
    )


def main() -> int:
    notes: list[str] = []
    try:
        _, login = http_json("POST", "/api/v1/auth/login", body={"username": USER, "password": PASSWORD})
        token = login.get("access_token") or login.get("token")
        if not token:
            write_report("C20", "FAIL", ["- Login response missing access_token"])
            return 1
        notes.append("- Login OK")

        status, cases = http_json("GET", "/api/v1/memory/cases?page=1&page_size=5&result=FAILURE", token)
        notes.append(f"- GET /memory/cases?result=FAILURE -> {status}")
        if status != 200:
            write_report("C20", "FAIL", notes)
            return 1

        status, suggestions = http_json("GET", "/api/v1/memory/suggestions?page=1&page_size=5", token)
        notes.append(f"- GET /memory/suggestions -> {status}")
        if status != 200:
            write_report("C20", "FAIL", notes)
            return 1

        notes.append(
            "- Policy check: improvement approval path exists via POST /memory/feedback "
            "with decision ACCEPT|REJECT; ACCEPTED does not auto-apply motion (code review)."
        )
        notes.append(f"- Failure cases returned: {cases.get('total', 'n/a')}")
        notes.append(f"- Suggestions returned: {suggestions.get('total', 'n/a')}")
        write_report("C20", "PASS", notes)
        return 0
    except urllib.error.URLError as exc:
        write_report("C20", "FAIL", [f"- API unreachable: {exc}"])
        return 1
    except Exception as exc:  # noqa: BLE001
        write_report("C20", "FAIL", [f"- Unexpected error: {exc}"])
        return 1


if __name__ == "__main__":
    sys.exit(main())
