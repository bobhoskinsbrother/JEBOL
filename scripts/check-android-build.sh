#!/usr/bin/env bash
# Checks that this machine builds and runs JEBOL's Android side, and writes
# what it found to build/android-check.txt to paste back. Goal 2b step 0;
# written for the owner's Mac, and runs the same on Ubuntu.
#
#   scripts/check-android-build.sh [class]
#
# It installs the Android SDK if there is none (scripts/setup-machine.sh),
# checks that the root build takes android/ in, then runs one device test class
# on one emulator it boots and shuts down itself: TheCoreBootsOnArtTest unless a
# class is named. The IDE half is by hand -- the report ends with it.

set -euo pipefail

cd "$(dirname "$0")/.."

class="${1:-org.jebol.android.TheCoreBootsOnArtTest}"
report=build/android-check.txt
mkdir -p build
: > "$report"

say() { printf '%s\n' "$*" | tee -a "$report"; }
verdict=ok
fail() { say "  FAIL  $*"; verdict=failed; }
pass() { say "  ok    $*"; }

say "JEBOL Android check, $(date '+%Y-%m-%d %H:%M:%S')"
say "machine: $(uname -s) $(uname -m)"
say "commit:  $(git rev-parse --short HEAD) $(git diff --quiet || echo '(with local changes)')"
say "class:   $class"
say ""

if [ "$(uname -s)" = Darwin ]; then
    default_sdk="$HOME/Library/Android/sdk"
else
    default_sdk="$HOME/Android/Sdk"
fi
sdk="${ANDROID_HOME:-$default_sdk}"
if [ ! -d "$sdk/platforms/android-35" ]; then
    say "No Android SDK with platform 35 in $sdk; running scripts/setup-machine.sh"
    scripts/setup-machine.sh 2>&1 | tee -a "$report"
fi
export ANDROID_HOME="$sdk"

say ""
say "== the build =="
if ./gradlew :android:host:assembleDebug --dry-run -q >/dev/null 2>&1; then
    pass "the root build includes android/"
else
    fail "the root build does not include android/ (is $sdk/platforms there?)"
fi
if [ -f android/local.properties ]; then
    pass "android/local.properties: $(cat android/local.properties)"
else
    fail "android/local.properties was not written"
fi

say ""
say "== one class on one emulator =="
started=$(date +%s)
if ./gradlew -p android androidTests -Pemulators=1 "-Pclasses=$class" 2>&1 | tee build/android-check-gradle.log | tail -20; then
    pass "androidTests -Pclasses=$class passed in $(( $(date +%s) - started ))s"
else
    fail "androidTests -Pclasses=$class failed after $(( $(date +%s) - started ))s; build/android-check-gradle.log has it all"
    tail -40 build/android-check-gradle.log >> "$report"
fi
newest=$(ls -td test-history/*-android 2>/dev/null | head -1 || true)
if [ -n "$newest" ]; then
    say "results: $newest"
    find "$newest" -name '*.xml' -exec grep -ho '<testsuite [^>]*' {} + \
        | sed -E 's/.*name="([^"]*)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*/  \1: \2 tests, \3 failures, \4 errors/' \
        | tee -a "$report"
fi

say ""
say "== by hand, in IntelliJ =="
say "  1. Settings > Plugins > Marketplace: install 'Android' (JetBrains), restart."
say "  2. Reload the Gradle project."
say "  3. Open android/host/src/main/java/org/jebol/adapter/android/AndroidHost.java:"
say "     'import android.' lines resolve, and the file is not marked as outside a source root."
say ""
say "verdict: $verdict"
say "Paste $report back."
[ "$verdict" = ok ]
