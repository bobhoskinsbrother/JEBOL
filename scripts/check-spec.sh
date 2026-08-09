#!/usr/bin/env bash
# Validate every Allium spec: structural check, then process analysis.
#
# No pipefail here on purpose. `allium check` exits non-zero on warnings
# alone, so the verdict comes from allium_report.py, which knows which
# warnings are the checker's own blind spots.
set -u

cd "$(dirname "$0")/.."

echo "== allium check =="
allium check spec/ 2>&1 | python3 scripts/allium_report.py
check_status=$?

echo
echo "== allium analyse =="
allium analyse spec/ 2>&1 | python3 scripts/allium_report.py
analyse_status=$?

if [ "$check_status" -ne 0 ] || [ "$analyse_status" -ne 0 ]; then
    exit 1
fi
exit 0
