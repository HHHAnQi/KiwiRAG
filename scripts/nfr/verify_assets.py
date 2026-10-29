#!/usr/bin/env python3
"""Fail-fast checks for production-hardening assets that do not require a live stack."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    alerts = (ROOT / "docs/operations/prometheus-alerts-ragdoc.yml").read_text()
    runbook = (ROOT / "docs/operations/alert-response-runbook.md").read_text()
    production = (ROOT / "platform-bootstrap/src/main/resources/application-prod.yml").read_text()
    drill = (ROOT / "scripts/v3-kill-9-drill.sh").read_text()

    alert_names = re.findall(r"^\s*- alert:\s*(\S+)", alerts, re.MULTILINE)
    require(alert_names, "no Prometheus alerts found")
    require(len(alert_names) == len(set(alert_names)), "duplicate Prometheus alert names")
    require(' or le=' not in alerts, "invalid PromQL label-selector boolean expression")
    for name in alert_names:
        require(name in runbook, f"alert has no runbook entry: {name}")

    require("dev-default-principal-enabled: false" in production, "prod auth is not fail-closed")
    require(re.search(r"(?m)^\s*mode:\s*async\s*$", production) is not None,
            "prod parser is not asynchronous")
    require("on-fail: REGENERATE" not in production,
            "prod enables an unsupported citation policy")
    require("/retry" not in drill, "crash drill still depends on the manual retry endpoint")
    require("ParseTaskOutboxRelay" in drill, "crash drill does not assert automatic outbox recovery")

    print(f"PASS: {len(alert_names)} alerts mapped to runbooks; prod and drill assets are consistent")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
