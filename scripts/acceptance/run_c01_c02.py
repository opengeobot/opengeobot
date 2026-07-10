#!/usr/bin/env python3
# Function: C01/C02 acceptance skeleton — IAM list/audit and dict/i18n checks
# Time: 2026-07-10
# Author: AxeXie
"""Run C01 (IAM/audit) and C02 (dict/i18n) acceptance checks against cloud-control.

Environment variables (optional):
  OPENGEOBOT_BASE_URL   default http://localhost:8080
  OPENGEOBOT_USERNAME   default admin
  OPENGEOBOT_PASSWORD   default admin123

Exit 0 if both C01 and C02 PASS; otherwise 1.
Writes reports/acceptance/C01-result.md and C02-result.md.
"""

from __future__ import annotations

import json
import os
import sys
import traceback
import urllib.error
import urllib.parse
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


class CheckError(Exception):
    """Single check failure with a human-readable message."""


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def write_report(name: str, status: str, lines: list[str]) -> Path:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    path = REPORT_DIR / name
    body = [
        f"<!--",
        f"Function: Acceptance result {name}",
        f"Time: {utc_now()}",
        f"Author: AxeXie",
        f"-->",
        "",
        f"# {name.replace('-result.md', '')} Acceptance Result",
        "",
        f"- **Status**: `{status}`",
        f"- **Timestamp**: `{utc_now()}`",
        f"- **Base URL**: `{BASE_URL}`",
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
    expected: set[int] | None = None,
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
            if expected is not None and status not in expected:
                raise CheckError(f"{method} {path} returned {status}, expected {sorted(expected)}")
            return status, payload
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        if expected is not None and exc.code in expected:
            try:
                return exc.code, json.loads(raw) if raw.strip() else None
            except json.JSONDecodeError:
                return exc.code, raw
        raise CheckError(f"{method} {path} HTTP {exc.code}: {raw[:500]}") from exc
    except urllib.error.URLError as exc:
        raise CheckError(f"API not reachable at {BASE_URL}: {exc.reason}") from exc
    except TimeoutError as exc:
        raise CheckError(f"API timeout contacting {BASE_URL}") from exc


def extract_items(payload: Any) -> list[Any]:
    if payload is None:
        return []
    if isinstance(payload, list):
        return payload
    if isinstance(payload, dict):
        for key in ("items", "records", "data", "content"):
            value = payload.get(key)
            if isinstance(value, list):
                return value
            if isinstance(value, dict) and isinstance(value.get("items"), list):
                return value["items"]
    return []


def login() -> str:
    status, payload = http_json(
        "POST",
        "/api/v1/auth/login",
        body={"username": USERNAME, "password": PASSWORD},
        expected={200},
    )
    if not isinstance(payload, dict):
        raise CheckError("login response is not a JSON object")
    token = payload.get("access_token") or payload.get("accessToken")
    if not token:
        raise CheckError(f"login 200 but missing access_token: keys={list(payload.keys())}")
    return str(token)


def require_ok(method: str, path: str, token: str, label: str) -> Any:
    status, payload = http_json(method, path, token=token, expected={200})
    return payload


def run_c01(token: str) -> list[str]:
    details: list[str] = []

    users = require_ok("GET", "/api/v1/users?page=1&pageSize=20", token, "users")
    details.append(f"GET /api/v1/users -> 200 (items={len(extract_items(users))})")

    orgs = require_ok("GET", "/api/v1/orgs?page=1&pageSize=20", token, "orgs")
    details.append(f"GET /api/v1/orgs -> 200 (items={len(extract_items(orgs))})")

    roles = require_ok("GET", "/api/v1/roles?page=1&pageSize=20", token, "roles")
    details.append(f"GET /api/v1/roles -> 200 (items={len(extract_items(roles))})")

    perms = require_ok("GET", "/api/v1/permissions", token, "permissions")
    details.append(f"GET /api/v1/permissions -> 200 (items={len(extract_items(perms) or (perms if isinstance(perms, list) else []))})")

    audits = require_ok("GET", "/api/v1/audits?page=1&pageSize=20", token, "audits")
    details.append(f"GET /api/v1/audits -> 200 (items={len(extract_items(audits))})")

    # Optional create + cleanup (disable) when manage endpoint allows it.
    suffix = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    username = f"acc_c01_{suffix}"
    try:
        status, created = http_json(
            "POST",
            "/api/v1/users",
            token=token,
            body={
                "username": username,
                "display_name": f"Acceptance {suffix}",
                "email": f"{username}@example.com",
                "password": "AccTest@123",
            },
            expected={201, 200, 403, 404, 405, 409},
        )
        if status in (200, 201) and isinstance(created, dict):
            user_id = created.get("user_id") or created.get("userId")
            details.append(f"POST /api/v1/users -> {status} created user_id={user_id}")
            if user_id:
                disable_status, _ = http_json(
                    "PATCH",
                    f"/api/v1/users/{user_id}/status",
                    token=token,
                    body={"status": "DISABLED", "reason": "acceptance cleanup"},
                    expected={200, 204},
                )
                details.append(f"PATCH /api/v1/users/{{id}}/status DISABLED -> {disable_status} (cleanup)")
        else:
            details.append(f"POST /api/v1/users optional create skipped/unavailable -> {status}")
    except CheckError as exc:
        details.append(f"optional create+cleanup skipped: {exc}")

    return details


def run_c02(token: str) -> list[str]:
    details: list[str] = []

    types_payload = require_ok("GET", "/api/v1/dict/types?page=1&pageSize=50", token, "dict types")
    type_items = extract_items(types_payload)
    details.append(f"GET /api/v1/dict/types -> 200 (types={len(type_items)})")

    type_codes: list[str] = []
    for item in type_items:
        if isinstance(item, dict):
            code = item.get("type_code") or item.get("typeCode") or item.get("code")
            if code:
                type_codes.append(str(code))
    if len(type_codes) != len(set(type_codes)):
        raise CheckError(f"duplicate dict type_code detected: {type_codes}")
    if type_codes:
        details.append(f"dict type_codes unique: {', '.join(type_codes[:10])}" + ("..." if len(type_codes) > 10 else ""))

    sample_code = "user_status" if "user_status" in type_codes else (type_codes[0] if type_codes else None)
    if sample_code:
        encoded = urllib.parse.quote(sample_code, safe="")
        items_payload = require_ok(
            "GET",
            f"/api/v1/dict/types/{encoded}/items?page=1&pageSize=50",
            token,
            "dict items",
        )
        details.append(
            f"GET /api/v1/dict/types/{sample_code}/items -> 200 (items={len(extract_items(items_payload))})"
        )

    zh = require_ok("GET", "/api/v1/i18n?locale=zh-CN&page=1&pageSize=100", token, "i18n zh-CN")
    en = require_ok("GET", "/api/v1/i18n?locale=en-US&page=1&pageSize=100", token, "i18n en-US")
    zh_items = extract_items(zh)
    en_items = extract_items(en)
    details.append(f"GET /api/v1/i18n?locale=zh-CN -> 200 (items={len(zh_items)})")
    details.append(f"GET /api/v1/i18n?locale=en-US -> 200 (items={len(en_items)})")

    def resource_keys(rows: list[Any]) -> set[str]:
        keys: set[str] = set()
        for row in rows:
            if isinstance(row, dict):
                key = row.get("resource_key") or row.get("resourceKey") or row.get("key")
                if key:
                    keys.add(str(key))
        return keys

    zh_keys = resource_keys(zh_items)
    en_keys = resource_keys(en_items)
    if zh_keys and en_keys:
        both = zh_keys & en_keys
        if not both:
            raise CheckError("i18n data exists for both locales but no shared resource_key")
        details.append(f"shared i18n resource_key count (zh-CN ∩ en-US) = {len(both)}")
    elif not zh_keys and not en_keys:
        details.append("i18n resources empty for both locales — list endpoints OK; bilingual pair check skipped")
    else:
        details.append(
            "i18n present in only one locale in sampled page — list OK; "
            "full bilingual coverage not asserted on this page"
        )

    return details


def main() -> int:
    c01_ok = False
    c02_ok = False
    c01_lines: list[str] = []
    c02_lines: list[str] = []

    try:
        token = login()
        c01_lines.append(f"login as {USERNAME} -> OK")
        c01_lines.extend(run_c01(token))
        c01_ok = True
    except CheckError as exc:
        c01_lines.append(f"FAIL: {exc}")
    except Exception as exc:  # noqa: BLE001 — acceptance harness must always write a report
        c01_lines.append(f"FAIL: unexpected error: {exc}")
        c01_lines.append(traceback.format_exc())

    write_report("C01-result.md", "PASS" if c01_ok else "FAIL", c01_lines)

    if c01_ok:
        try:
            token = login()
            c02_lines.append(f"login as {USERNAME} -> OK")
            c02_lines.extend(run_c02(token))
            c02_ok = True
        except CheckError as exc:
            c02_lines.append(f"FAIL: {exc}")
        except Exception as exc:  # noqa: BLE001
            c02_lines.append(f"FAIL: unexpected error: {exc}")
            c02_lines.append(traceback.format_exc())
    else:
        # Still attempt C02 independently if login might work — but if C01 failed on reachability, mirror FAIL.
        try:
            token = login()
            c02_lines.append(f"login as {USERNAME} -> OK")
            c02_lines.extend(run_c02(token))
            c02_ok = True
        except CheckError as exc:
            c02_lines.append(f"FAIL: {exc}")
        except Exception as exc:  # noqa: BLE001
            c02_lines.append(f"FAIL: unexpected error: {exc}")
            c02_lines.append(traceback.format_exc())

    write_report("C02-result.md", "PASS" if c02_ok else "FAIL", c02_lines)

    print(f"C01: {'PASS' if c01_ok else 'FAIL'} -> {REPORT_DIR / 'C01-result.md'}")
    print(f"C02: {'PASS' if c02_ok else 'FAIL'} -> {REPORT_DIR / 'C02-result.md'}")
    return 0 if (c01_ok and c02_ok) else 1


if __name__ == "__main__":
    sys.exit(main())
