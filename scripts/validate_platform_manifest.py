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
MANDATORY_DONE_EVIDENCE = {
    "DEPLOYMENT",
    "OBSERVABILITY",
    "SECURITY_REPORTS",
    "TEST_REPORTS",
    "RUNBOOK",
}
REQUIRED_PLATFORM_CAPABILITIES = {
    "CONTRACT_GOVERNANCE": "F-ENGINEERING-001",
    "ERROR_MODEL": "F-ENGINEERING-001",
    "EVENT_ENVELOPE": "F-ENGINEERING-001",
    "OBSERVABILITY": "F-ENGINEERING-001",
    "TIME_ID": "F-ENGINEERING-001",
    "TRACE_CONTEXT": "F-ENGINEERING-001",
    "IDENTITY": "F-PLATFORM-001",
    "AUTHORIZATION": "F-PLATFORM-002",
    "DATA_SCOPE": "F-PLATFORM-002",
    "DICTIONARY": "F-PLATFORM-003",
    "I18N": "F-PLATFORM-003",
    "CONFIGURATION": "F-PLATFORM-004",
    "AUDIT": "F-PLATFORM-004",
    "IDEMPOTENCY": "F-PLATFORM-004",
    "OBJECT_METADATA": "F-MEDIA-001",
    "SAFETY_ENFORCEMENT": "F-SAFETY-001",
    "OPERATIONS_GOVERNANCE": "F-OPS-001",
}
REFERENCE_FIELDS = {
    "page_ids": (
        "frontend",
        "page_ids",
        r"(?<![A-Z0-9*])P-[A-Z]+-[A-Z0-9]+(?![A-Z0-9*-])",
    ),
    "use_case_ids": (
        "backend",
        "use_case_ids",
        r"(?<![A-Z0-9*])UC-[A-Z]+-[A-Z0-9-]+(?<!-)(?![A-Z0-9*-])",
    ),
    "state_machine_ids": (
        "data",
        "state_machine_ids",
        r"(?<![A-Z0-9*])SM-[A-Z]+-[A-Z0-9-]+(?<!-)(?![A-Z0-9*-])",
    ),
}
EVIDENCE_KEYS = {
    "CONTRACTS": "contracts",
    "MIGRATIONS": "migrations",
    "BACKEND": "backend",
    "FRONTEND": "frontend",
    "EVENTS": "events",
    "DEPLOYMENT": "deployment",
    "OBSERVABILITY": "observability",
    "SECURITY_REPORTS": "security_reports",
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
PHASE_ORDER = {f"M{number}": number for number in range(7)}
ACTIVE_STATUSES = {"IN_PROGRESS", "DONE"}
COMPLETED_PREREQUISITE_STATUSES = {"DONE", "NOT_APPLICABLE"}
DISALLOWED_EVIDENCE_PREFIXES = (".git/", "tmp/")
DISALLOWED_GENERIC_EVIDENCE_PATHS = {
    "apps",
    "apps/web-console",
    "contracts",
    "deploy",
    "deploy/observability",
    "docs/runbooks",
    "packages",
    "reports",
    "reports/deployment",
    "reports/hil",
    "reports/observability",
    "reports/security",
    "reports/tests",
    "scripts",
}
FEATURE_SCOPED_EVIDENCE = {
    "DEPLOYMENT",
    "OBSERVABILITY",
    "SECURITY_REPORTS",
    "TEST_REPORTS",
    "HIL_REPORT",
}
EVIDENCE_ALLOWED_PREFIXES = {
    "CONTRACTS": ("contracts/",),
    "MIGRATIONS": ("apps/",),
    "BACKEND": ("apps/", "packages/", "scripts/", "deploy/"),
    "FRONTEND": ("apps/web-console/",),
    "EVENTS": ("contracts/",),
    "DEPLOYMENT": ("deploy/", "reports/deployment/"),
    "OBSERVABILITY": (
        "deploy/observability/",
        "reports/observability/",
        "apps/",
    ),
    "SECURITY_REPORTS": ("reports/security/",),
    "TEST_REPORTS": ("reports/tests/",),
    "RUNBOOK": ("docs/runbooks/",),
    "HIL_REPORT": ("reports/hil/",),
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


def path_has_content(path: Path) -> bool:
    if path.is_file():
        return path.stat().st_size > 0
    if not path.is_dir():
        return False
    return any(
        candidate.is_file() and candidate.stat().st_size > 0
        for candidate in path.rglob("*")
    )


def validate_governance(manifest: dict[str, Any], errors: list[str]) -> None:
    features = manifest["features"]
    feature_by_id = {feature["id"]: feature for feature in features}
    feature_ids = set(feature_by_id)

    decisions = set(manifest["architecture_decisions"])
    missing_decision_sources = sorted(decisions - set(manifest["sources"]))
    if missing_decision_sources:
        errors.append(
            "architecture decisions absent from sources: "
            + ", ".join(missing_decision_sources)
        )

    mandatory_evidence = set(manifest["mandatory_done_evidence"])
    if mandatory_evidence != MANDATORY_DONE_EVIDENCE:
        errors.append(
            "mandatory_done_evidence must be exactly: "
            + ", ".join(sorted(MANDATORY_DONE_EVIDENCE))
        )

    capabilities = manifest["platform_capabilities"]
    if capabilities != REQUIRED_PLATFORM_CAPABILITIES:
        missing = sorted(
            f"{name}={owner}"
            for name, owner in REQUIRED_PLATFORM_CAPABILITIES.items()
            if capabilities.get(name) != owner
        )
        unexpected = sorted(set(capabilities) - set(REQUIRED_PLATFORM_CAPABILITIES))
        details = missing + [f"unexpected:{name}" for name in unexpected]
        errors.append("platform capability ownership drift: " + ", ".join(details))

    unknown_owners = sorted(set(capabilities.values()) - feature_ids)
    if unknown_owners:
        errors.append(
            "platform capability owners absent from features: "
            + ", ".join(unknown_owners)
        )

    profiles = manifest["platform_capability_profiles"]
    known_capabilities = set(capabilities)
    for profile_name, profile_capabilities in profiles.items():
        unknown = sorted(set(profile_capabilities) - known_capabilities)
        if unknown:
            errors.append(
                f"platform profile {profile_name} has unknown capabilities: "
                + ", ".join(unknown)
            )

    for feature in features:
        identifier = feature["id"]
        profile_name = feature["platform_capability_profile"]
        if profile_name not in profiles:
            errors.append(f"{identifier} uses unknown platform profile {profile_name}")
            continue
        profile_capabilities = set(profiles[profile_name])
        if feature["phase"] != "M0" and profile_name == "ENGINEERING_CORE":
            errors.append(f"{identifier} cannot use M0-only ENGINEERING_CORE profile")
        if (
            feature["security"]["requires_edge_safety"]
            and "SAFETY_ENFORCEMENT" not in profile_capabilities
        ):
            errors.append(
                f"{identifier} requires edge safety but its platform profile "
                "does not include SAFETY_ENFORCEMENT"
            )

    for feature in features:
        if feature["implementation_status"] not in ACTIVE_STATUSES:
            continue
        phase_number = PHASE_ORDER[feature["phase"]]
        incomplete_prerequisites = sorted(
            other["id"]
            for other in features
            if PHASE_ORDER[other["phase"]] < phase_number
            and other["implementation_status"] not in COMPLETED_PREREQUISITE_STATUSES
        )
        if incomplete_prerequisites:
            errors.append(
                f"{feature['id']} is {feature['implementation_status']} before "
                "earlier phases completed: "
                + ", ".join(incomplete_prerequisites)
            )

    external_ids: list[str] = []
    for contract in manifest["external_contracts"]:
        external_ids.append(contract["id"])
        unknown_features = sorted(set(contract["required_by"]) - feature_ids)
        if unknown_features:
            errors.append(
                f"{contract['id']} required_by has unknown features: "
                + ", ".join(unknown_features)
            )
        active_consumers = sorted(
            identifier
            for identifier in contract["required_by"]
            if identifier in feature_by_id
            and feature_by_id[identifier]["implementation_status"] in ACTIVE_STATUSES
        )
        if active_consumers and contract["pin_status"] != "PINNED":
            errors.append(
                f"{contract['id']} is UNPINNED but consumers are active: "
                + ", ".join(active_consumers)
            )
        if contract["pin_status"] == "PINNED":
            reference = contract.get("locked_reference", "")
            try:
                reference_path = repository_path(reference)
            except ValueError as exc:
                errors.append(f"{contract['id']} {exc}")
                continue
            if not path_has_content(reference_path):
                errors.append(
                    f"{contract['id']} locked reference is missing or empty: "
                    f"{reference}"
                )

    duplicate_external_ids = sorted(
        identifier
        for identifier in set(external_ids)
        if external_ids.count(identifier) > 1
    )
    if duplicate_external_ids:
        errors.append(
            "duplicate external contract ids: " + ", ".join(duplicate_external_ids)
        )


def validate_feature_safety(features: list[dict[str, Any]], errors: list[str]) -> None:
    for feature in features:
        identifier = feature["id"]
        security = feature["security"]
        tests = set(feature["verification"]["required_tests"])
        if security["requires_edge_safety"]:
            if security["risk_level"] not in {"R2_BOUNDED_MOTION", "R3_HIGH_RISK"}:
                errors.append(
                    f"{identifier} requires edge safety with invalid risk level "
                    f"{security['risk_level']}"
                )
            missing_tests = {"SIMULATION", "SECURITY"} - tests
            if missing_tests:
                errors.append(
                    f"{identifier} requires edge safety but lacks tests: "
                    + ", ".join(sorted(missing_tests))
                )
        elif security["risk_level"] == "R2_BOUNDED_MOTION":
            errors.append(
                f"{identifier} is bounded motion but requires_edge_safety is false"
            )


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

    ambiguous_ranges = sorted(
        set(
            re.findall(
                r"\b(?:UC|SM)-[A-Z]+-[A-Z0-9-]*[0-9]{3}-[0-9]{3}\b",
                blueprint_text,
            )
        )
    )
    if ambiguous_ranges:
        errors.append(
            "blueprint contains ambiguous range-shaped ids: "
            + ", ".join(ambiguous_ranges)
        )

    for label, (section, field, pattern) in REFERENCE_FIELDS.items():
        manifest_values = {
            value
            for feature in features
            for value in feature.get(section, {}).get(field, [])
        }
        blueprint_values = set(re.findall(pattern, blueprint_text))
        missing = sorted(manifest_values - blueprint_values)
        untracked = sorted(blueprint_values - manifest_values)
        if missing:
            errors.append(f"{label} absent from blueprint: {', '.join(missing)}")
        if untracked:
            errors.append(f"{label} absent from manifest: {', '.join(untracked)}")

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
        required_evidence = declared_evidence | MANDATORY_DONE_EVIDENCE
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
        for category in sorted(required_evidence):
            key = EVIDENCE_KEYS[category]
            paths = evidence.get(key, [])
            if not paths:
                errors.append(f"{identifier} DONE without {category} evidence")
                continue
            normalized_paths = [
                value.replace("\\", "/").lstrip("./") for value in paths
            ]
            if category in FEATURE_SCOPED_EVIDENCE and not any(
                identifier in value for value in normalized_paths
            ):
                errors.append(
                    f"{identifier} {category} evidence has no feature-scoped path"
                )
            for value in paths:
                try:
                    path = repository_path(value)
                except ValueError as exc:
                    errors.append(f"{identifier} {exc}")
                    continue
                normalized = value.replace("\\", "/").lstrip("./")
                if normalized.startswith(DISALLOWED_EVIDENCE_PREFIXES):
                    errors.append(
                        f"{identifier} {category} evidence uses disallowed path: {value}"
                    )
                    continue
                if normalized.rstrip("/") in DISALLOWED_GENERIC_EVIDENCE_PATHS:
                    errors.append(
                        f"{identifier} {category} evidence is too broad: {value}"
                    )
                    continue
                allowed_prefixes = EVIDENCE_ALLOWED_PREFIXES[category]
                if not normalized.startswith(allowed_prefixes):
                    errors.append(
                        f"{identifier} {category} evidence must be under "
                        f"{', '.join(allowed_prefixes)}: {value}"
                    )
                if not path_has_content(path):
                    errors.append(
                        f"{identifier} evidence is missing or empty: {value}"
                    )


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
        validate_governance(manifest, errors)
        validate_feature_safety(features, errors)
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
