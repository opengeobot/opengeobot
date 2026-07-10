#!/usr/bin/env python3
# Function: C23/C24 structural acceptance check — dev.sh, compose, kubernetes, CI
# Time: 2026-07-10
# Author: AxeXie
"""Structural checks for C23 (unified dev script) and C24 (compose + k8s/CI baseline).

This script verifies repository artifacts required by C23/C24:
  - scripts/dev.sh exists and is executable
  - deploy/compose/compose.yml exists
  - deploy/kubernetes/ directory exists (baseline manifests)
  - .github/workflows/ci.yml exists

It does NOT run full runtime verification (doctor/infra-up/e2e, security scans).
Those remain covered by docs/test-plans/c23-c24-test-plan.md.

Writes reports/acceptance/C23-result.md and C24-result.md.
Exit 0 if both structural checks PASS; otherwise 1.
"""

from __future__ import annotations

import os
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPORT_DIR = ROOT / "reports" / "acceptance"
DEV_SH = ROOT / "scripts" / "dev.sh"
COMPOSE_YML = ROOT / "deploy" / "compose" / "compose.yml"
K8S_DIR = ROOT / "deploy" / "kubernetes"
CI_YML = ROOT / ".github" / "workflows" / "ci.yml"


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def write_report(name: str, status: str, lines: list[str]) -> Path:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    path = REPORT_DIR / name
    body = [
        "<!--",
        f"Function: Acceptance result {name}",
        f"Time: {utc_now()}",
        "Author: AxeXie",
        "-->",
        "",
        f"# {name.replace('-result.md', '')} Acceptance Result",
        "",
        f"- **Status**: `{status}`",
        f"- **Timestamp**: `{utc_now()}`",
        f"- **Scope**: structural check only (full runtime verification is separate)",
        "",
        "## Details",
        "",
    ]
    body.extend(f"- {line}" for line in lines)
    body.append("")
    path.write_text("\n".join(body), encoding="utf-8")
    return path


def check_c23() -> tuple[bool, list[str]]:
    details: list[str] = []
    if not DEV_SH.is_file():
        details.append(f"FAIL: missing file {DEV_SH.relative_to(ROOT)}")
        return False, details

    details.append(f"found {DEV_SH.relative_to(ROOT)}")
    if not os.access(DEV_SH, os.X_OK):
        details.append("FAIL: scripts/dev.sh exists but is not executable")
        return False, details

    details.append("scripts/dev.sh is executable")
    details.append(
        "NOTE: full C23 runtime (doctor/bootstrap/infra-up/migrate/dev/test/down) "
        "must still be executed per docs/test-plans/c23-c24-test-plan.md"
    )
    return True, details


def check_c24() -> tuple[bool, list[str]]:
    details: list[str] = []
    ok = True

    if not COMPOSE_YML.is_file():
        details.append(f"FAIL: missing file {COMPOSE_YML.relative_to(ROOT)}")
        ok = False
    else:
        details.append(f"found {COMPOSE_YML.relative_to(ROOT)}")

    if not K8S_DIR.is_dir():
        details.append(f"FAIL: missing directory {K8S_DIR.relative_to(ROOT)}")
        ok = False
    else:
        details.append(f"found {K8S_DIR.relative_to(ROOT)}/")
        expected = [
            "namespace.yaml",
            "postgres.yaml",
            "nats.yaml",
            "minio.yaml",
            "cloud-control.yaml",
            "web-console.yaml",
            "configmap.yaml",
            "secret.yaml.example",
            "README.md",
        ]
        for name in expected:
            path = K8S_DIR / name
            if path.is_file():
                details.append(f"found deploy/kubernetes/{name}")
            else:
                details.append(f"FAIL: missing deploy/kubernetes/{name}")
                ok = False

    if not CI_YML.is_file():
        details.append(f"FAIL: missing file {CI_YML.relative_to(ROOT)}")
        ok = False
    else:
        details.append(f"found {CI_YML.relative_to(ROOT)}")

    if ok:
        details.append(
            "NOTE: structural PASS for compose + kubernetes baseline + CI workflow presence; "
            "full C24 runtime (profiles, health, persistence) and security scans "
            "(越权/注入/重放/Secret/供应链) remain separate — C24 must not be marked NOT_APPLICABLE"
        )
    return ok, details


def main() -> int:
    c23_ok, c23_lines = check_c23()
    c24_ok, c24_lines = check_c24()

    write_report("C23-result.md", "PASS" if c23_ok else "FAIL", c23_lines)
    write_report("C24-result.md", "PASS" if c24_ok else "FAIL", c24_lines)

    print(f"C23: {'PASS' if c23_ok else 'FAIL'} -> {REPORT_DIR / 'C23-result.md'}")
    print(f"C24: {'PASS' if c24_ok else 'FAIL'} -> {REPORT_DIR / 'C24-result.md'}")
    return 0 if (c23_ok and c24_ok) else 1


if __name__ == "__main__":
    sys.exit(main())
