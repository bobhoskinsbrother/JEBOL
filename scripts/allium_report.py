#!/usr/bin/env python3
"""Summarise `allium check` / `allium analyse` output.

The CLI prints one JSON object per spec file with no separator between
them, so the stream is decoded object by object rather than parsed in one
go. It also exits non-zero on mere warnings, which is why this script
judges the diagnostics itself rather than trusting that exit code.

Fails on any error, any analysis finding, and any warning whose message is
not in spec/.allium-warning-allowlist.
"""

import json
import os
import sys

ALLOWLIST_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "spec",
    ".allium-warning-allowlist",
)


def read_allowlist():
    if not os.path.exists(ALLOWLIST_PATH):
        return set()
    with open(ALLOWLIST_PATH, encoding="utf-8") as handle:
        return {
            line.strip()
            for line in handle
            if line.strip() and not line.startswith("#")
        }


def read_reports(text):
    decoder = json.JSONDecoder()
    reports = []
    at = 0
    while at < len(text):
        while at < len(text) and text[at].isspace():
            at += 1
        if at >= len(text):
            break
        report, at = decoder.raw_decode(text, at)
        reports.append(report)
    return reports


def describe(diagnostic):
    location = diagnostic["location"]
    file_name = location["file"].rsplit("/", 1)[-1]
    return (
        f"{diagnostic['severity']:8} {file_name:16} "
        f"L{location['line']:<4} {diagnostic['code']}: {diagnostic['message']}"
    )


def main():
    allowlist = read_allowlist()
    reports = read_reports(sys.stdin.read())

    errors = 0
    unexpected_warnings = 0
    allowed_warnings = 0
    findings = 0

    for report in reports:
        for diagnostic in report.get("diagnostics", []):
            severity = diagnostic["severity"]
            if severity == "error":
                errors += 1
                print(describe(diagnostic))
            elif severity == "warning":
                if diagnostic["message"] in allowlist:
                    allowed_warnings += 1
                else:
                    unexpected_warnings += 1
                    print(describe(diagnostic))
        for finding in report.get("findings", []):
            findings += 1
            print(f"finding  {json.dumps(finding)}")

    print(
        f"{len(reports)} spec file(s): {errors} error(s), "
        f"{unexpected_warnings} unexpected warning(s), {findings} finding(s), "
        f"{allowed_warnings} allowlisted warning(s)"
    )
    return 1 if errors or unexpected_warnings or findings else 0


if __name__ == "__main__":
    sys.exit(main())
