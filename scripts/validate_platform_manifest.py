#!/usr/bin/env python3
"""Validate the platform implementation manifest and its traceability."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import yaml
from jsonschema import Draft202012Validator


ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "docs/implementation/platform-feature-manifest.yaml"
SCHEMA_PATH = ROOT / "docs/implementation/platform-feature-manifest.schema.json"
REQUIRED_ACCEPTANCE = {f"C{number:02d}" for number in range(1, 25)}
REFERENCE_FIELDS = {
    "page_ids": ("frontend", "page_ids"),
    "use_case_ids": ("backend", "use_case_ids"),
    "state_machine_ids": ("data", "state_machine_ids"),
}
EVIDENCE_KEYS = {
    "CONTRACTS": "contracts",
    "MIGRATIONS": "migrations",
    "BACKEND": "backend",
    "FRONTEND": "frontend",
    "EVENTS": "events",
    "TEST_REPORTS": "test_reports",
    "RUNBOOK": "runbook",
    "HIL_REPORT": "hil_report",
}
ARTIFACT_REQUIREMENTS = {
    "contracts": "CONTRACTS",
    "tables": "MIGRATIONS",
    "modules": "BACKEND",
    "page_ids": "FRONTEND",
    "events": "EVENTS",
}


def load_document(path: Path) -> Any:
    text = path.read_text(encoding="utf-8")
    if path.suffix == ".json":
        return json.loads(text)
    return yaml.safe_load(text)


def repository_path(value: str) -> Path:
    path = (ROOT / value).resolve()
    try:
        path.relative_to(ROOT.resolve())
    except ValueError as exc:
        raise ValueError(f"path escapes repository: {value}") from exc
    return path


def find_blueprint(manifest: dict[str, Any]) -> tuple[Path, str]:
    for source in manifest["sources"]:
        path = repository_path(source)
        if not path.exists():
            raise ValueError(f"source does not exist: {source}")
        if path.suffix == ".md":
            text = path.read_text(encoding="utf-8")
            if "F-PLATFORM-001" in text and "SM-MISSION-001" in text:
                return path, text
    raise ValueError("implementation blueprint was not found in manifest sources")


def validate_sources(manifest: dict[str, Any], errors: list[str]) -> None:
    for source in manifest["sources"]:
        try:
            path = repository_path(source)
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if not path.exists():
            errors.append(f"source does not exist: {source}")


def validate_schema(
    manifest: dict[str, Any], schema: dict[str, Any], errors: list[str]
) -> None:
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for error in sorted(validator.iter_errors(manifest), key=lambda item: list(item.path)):
        location = "/".join(str(part) for part in error.path) or "<root>"
        errors.append(f"schema {location}: {error.message}")


def validate_unique_features(features: list[dict[str, Any]], errors: list[str]) -> None:
    identifiers = [feature["id"] for feature in features if "id" in feature]
    duplicates = sorted(
        identifier for identifier in set(identifiers) if identifiers.count(identifier) > 1
    )
    if duplicates:
        errors.append(f"duplicate feature ids: {', '.join(duplicates)}")


def validate_traceability(
    features: list[dict[str, Any]], blueprint_text: str, errors: list[str]
) -> None:
    manifest_ids = {feature["id"] for feature in features}
    blueprint_ids = set(re.findall(r"F-[A-Z]+-[0-9]{3}", blueprint_text))
    missing_features = sorted(manifest_ids - blueprint_ids)
    unknown_features = sorted(blueprint_ids - manifest_ids)
    if missing_features:
        errors.append(
            f"features absent from blueprint: {', '.join(missing_features)}"
        )
    if unknown_features:
        errors.append(
            f"blueprint features absent from manifest: {', '.join(unknown_features)}"
        )

    for label, (section, field) in REFERENCE_FIELDS.items():
        values = {
            value
            for feature in features
            for value in feature.get(section, {}).get(field, [])
        }
        missing = sorted(value for value in values if value not in blueprint_text)
        if missing:
            errors.append(f"{label} absent from blueprint: {', '.join(missing)}")

    covered_acceptance = {
        acceptance
        for feature in features
        for acceptance in feature.get("verification", {}).get("acceptance_ids", [])
    }
    missing_acceptance = sorted(REQUIRED_ACCEPTANCE - covered_acceptance)
    unexpected_acceptance = sorted(covered_acceptance - REQUIRED_ACCEPTANCE)
    if missing_acceptance:
        errors.append(
            f"acceptance ids not covered: {', '.join(missing_acceptance)}"
        )
    if unexpected_acceptance:
        errors.append(
            f"unknown acceptance ids: {', '.join(unexpected_acceptance)}"
        )


def validate_done_evidence(
    features: list[dict[str, Any]], require_complete: bool, errors: list[str]
) -> None:
    for feature in features:
        identifier = feature["id"]
        status = feature["implementation_status"]
        declared_evidence = set(feature["verification"]["done_evidence"])
        actual_artifacts = {
            "contracts": feature["contracts"],
            "tables": feature["data"]["tables"],
            "modules": feature["backend"]["modules"],
            "page_ids": feature["frontend"]["page_ids"],
            "events": feature["events"],
        }
        for artifact, required_category in ARTIFACT_REQUIREMENTS.items():
            if actual_artifacts[artifact] and required_category not in declared_evidence:
                errors.append(
                    f"{identifier} has {artifact} but does not require "
                    f"{required_category} evidence"
                )
        if "TEST_REPORTS" not in declared_evidence:
            errors.append(f"{identifier} does not require TEST_REPORTS evidence")
        if (
            "HIL" in feature["verification"]["required_tests"]
            and "HIL_REPORT" not in declared_evidence
        ):
            errors.append(f"{identifier} requires HIL but not HIL_REPORT evidence")

        if require_complete and status != "DONE":
            errors.append(f"{identifier} is {status}, expected DONE")
        if status != "DONE":
            continue

        evidence = feature.get("evidence", {})
        for category in feature["verification"]["done_evidence"]:
            key = EVIDENCE_KEYS[category]
            paths = evidence.get(key, [])
            if not paths:
                errors.append(f"{identifier} DONE without {category} evidence")
                continue
            for value in paths:
                try:
                    path = repository_path(value)
                except ValueError as exc:
                    errors.append(f"{identifier} {exc}")
                    continue
                if not path.exists():
                    errors.append(f"{identifier} evidence does not exist: {value}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--require-complete",
        action="store_true",
        help="also require every feature to be DONE with existing evidence files",
    )
    args = parser.parse_args()

    errors: list[str] = []
    try:
        manifest = load_document(MANIFEST_PATH)
        schema = load_document(SCHEMA_PATH)
        validate_sources(manifest, errors)
        _, blueprint_text = find_blueprint(manifest)
        validate_schema(manifest, schema, errors)
        features = manifest.get("features", [])
        validate_unique_features(features, errors)
        validate_traceability(features, blueprint_text, errors)
        validate_done_evidence(features, args.require_complete, errors)
    except (
        KeyError,
        TypeError,
        OSError,
        ValueError,
        json.JSONDecodeError,
        yaml.YAMLError,
    ) as exc:
        errors.append(str(exc))

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    statuses: dict[str, int] = {}
    for feature in manifest["features"]:
        status = feature["implementation_status"]
        statuses[status] = statuses.get(status, 0) + 1
    summary = ", ".join(f"{key}={statuses[key]}" for key in sorted(statuses))
    page_ids = {
        value
        for feature in manifest["features"]
        for value in feature["frontend"]["page_ids"]
    }
    use_case_ids = {
        value
        for feature in manifest["features"]
        for value in feature["backend"]["use_case_ids"]
    }
    state_machine_ids = {
        value
        for feature in manifest["features"]
        for value in feature["data"]["state_machine_ids"]
    }
    print(
        f"PASS: features={len(manifest['features'])}; pages={len(page_ids)}; "
        f"use_cases={len(use_case_ids)}; state_machines={len(state_machine_ids)}; "
        f"C01-C24 covered; {summary}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
