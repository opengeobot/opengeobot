"""
功能：Docker Compose 环境简单验证
时间：2026-05-08 16:57:00
作者：AxeXie
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from urllib import request

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def main() -> None:
    print("[1/3] Health check")
    req = request.Request("http://127.0.0.1:8010/health")
    with request.urlopen(req, timeout=10) as response:
        health = json.loads(response.read().decode("utf-8"))
        trace_id = response.getheader("X-Trace-Id", "")
        print(f"  health: {health}, traceId: {trace_id}")

    print("[2/3] Create project")
    project_body = {
        "project_name": "Docker Final Test",
        "project_type": "website",
        "source_url": "https://example.com",
        "brand_name": "FinalTest",
        "language": "zh-CN",
        "region": "global",
        "aliases": [],
        "competitors": [],
    }
    req = request.Request(
        "http://127.0.0.1:8010/projects",
        data=json.dumps(project_body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=10) as response:
        project = json.loads(response.read().decode("utf-8"))
        trace_id = response.getheader("X-Trace-Id", "")
        project_id = project["project_id"]
        print(f"  project_id: {project_id}, traceId: {trace_id}")

    print("[3/3] Generate prompts")
    prompt_body = {"count": 20}
    req = request.Request(
        f"http://127.0.0.1:8010/projects/{project_id}/prompts/generate",
        data=json.dumps(prompt_body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=10) as response:
        result = json.loads(response.read().decode("utf-8"))
        print(f"  generated: {len(result) if isinstance(result, list) else 'success'}")

    print("\n=== Docker Compose Verification Result ===")
    print("OpenGEO Bot API is healthy and responding correctly")


if __name__ == "__main__":
    main()