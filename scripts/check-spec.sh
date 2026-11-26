#!/usr/bin/env bash
# Validate every Allium spec: structural check, then process analysis.
#
# No pipefail here on purpose. `allium check` exits non-zero on warnings
# alone, so the verdict comes from allium_report.py, which knows which
# warnings are the checker's own blind spots.
set -u

cd "$(dirname "$0")/.."

allium="$(command -v allium || true)"
if [ -z "$allium" ] && [ -x "$HOME/.cargo/bin/allium" ]; then
    allium="$HOME/.cargo/bin/allium"
fi
pinned="$(cat scripts/allium-version)"
install="cargo install allium-cli --version $pinned --locked --force"
if [ -z "$allium" ]; then
    echo "allium is not on the PATH or in ~/.cargo/bin, so spec/ cannot be checked." >&2
    echo "    $install" >&2
    exit 1
fi
installed="$("$allium" --version 2>&1 | head -1)"
case "$installed" in
    "allium $pinned "*) ;;
    *)
        echo "spec/ is checked with allium $pinned, and $allium is '$installed'." >&2
        echo "Another version reads the spec differently, so its answer is not this build's." >&2
        echo "    $install" >&2
        exit 1
        ;;
esac

echo "== allium check =="
"$allium" check spec/ 2>&1 | python3 scripts/allium_report.py
check_status=$?

echo
echo "== allium analyse =="
"$allium" analyse spec/ 2>&1 | python3 scripts/allium_report.py
analyse_status=$?

if [ "$check_status" -ne 0 ] || [ "$analyse_status" -ne 0 ]; then
    exit 1
fi
exit 0
