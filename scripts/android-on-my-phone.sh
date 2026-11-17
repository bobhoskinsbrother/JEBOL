#!/usr/bin/env bash
# Puts the JEBOL app on a phone plugged in over USB, measures it, and leaves it
# installed to be used by hand. Goal 2b step 9. Writes everything it measured
# to build/phone-timings.txt, which is the file to paste back.
#
#   scripts/android-on-my-phone.sh [--with-tests] [--profile] [serial]
#
#   --with-tests   also run androidEndToEndTests on the phone (-Pdevice)
#   --profile      after the timings, record a ten second simpleperf profile
#                  while you drag the slider, into build/phone-drag.perf.data
#   serial         which device, when more than one is plugged in
#
# Before running it: on the phone, Settings > About phone > tap Build number
# seven times, then Settings > System > Developer options > USB debugging on.
# Plug it in and accept "Allow USB debugging?" on the phone.

set -euo pipefail

cd "$(dirname "$0")/.."

with_tests=false
profile=false
serial=""
for argument in "$@"; do
    case "$argument" in
    --with-tests) with_tests=true ;;
    --profile) profile=true ;;
    -*) echo "unknown switch $argument; see the top of $0" >&2; exit 2 ;;
    *) serial=$argument ;;
    esac
done

if [ "$(uname -s)" = Darwin ]; then
    sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
else
    sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
fi
adb="$sdk/platform-tools/adb"
if [ ! -x "$adb" ]; then
    echo "No adb in $sdk/platform-tools; run scripts/setup-machine.sh first." >&2
    exit 1
fi
export ANDROID_HOME="$sdk"

report=build/phone-timings.txt
mkdir -p build
: > "$report"
say() { printf '%s\n' "$*" | tee -a "$report"; }

"$adb" start-server >/dev/null
if [ -z "$serial" ]; then
    attached=$("$adb" devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ {print $1}')
    unauthorised=$("$adb" devices | awk 'NR > 1 && $2 == "unauthorized" {print $1}')
    if [ -n "$unauthorised" ]; then
        echo "The phone $unauthorised has not allowed USB debugging yet: accept the prompt on it, then run this again." >&2
        exit 1
    fi
    count=$(printf '%s' "$attached" | grep -c . || true)
    if [ "$count" -ne 1 ]; then
        echo "Expected exactly one phone plugged in and allowed, found $count: ${attached:-none}." >&2
        echo "Name the one to use: $0 <serial>" >&2
        exit 1
    fi
    serial=$attached
fi
on_phone() { "$adb" -s "$serial" "$@"; }

level=$(on_phone shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$level" -lt 35 ]; then
    echo "This phone runs API $level (Android $(on_phone shell getprop ro.build.version.release | tr -d '\r'));" >&2
    echo "the app needs API 35, Android 15, at the least (decision 73). It cannot be installed here." >&2
    exit 1
fi

say "JEBOL on a phone, $(date '+%Y-%m-%d %H:%M:%S')"
say "phone:   $(on_phone shell getprop ro.product.manufacturer | tr -d '\r') $(on_phone shell getprop ro.product.model | tr -d '\r'), Android $(on_phone shell getprop ro.build.version.release | tr -d '\r') (API $level)"
say "screen:  $(on_phone shell wm size | tr -d '\r' | tail -1), $(on_phone shell wm density | tr -d '\r' | tail -1)"
say "commit:  $(git rev-parse --short HEAD) $(git diff --quiet || echo '(with local changes)')"
say ""

./gradlew -p android :app:assembleDebug :endtoend:assembleDebug -q
app_apk=android/app/build/outputs/apk/debug/app-debug.apk
test_apk=android/endtoend/build/outputs/apk/debug/endtoend-debug.apk
say "app APK: $(du -k "$app_apk" | cut -f1) KB"
on_phone install -r -t "$app_apk" >/dev/null
on_phone install -r -t "$test_apk" >/dev/null

say ""
say "== timings, on $(on_phone shell getprop ro.product.model | tr -d '\r') =="
measured_on_phone=/sdcard/Android/data/org.jebol.android.endtoend/files/timings.txt
on_phone shell rm -f "$measured_on_phone"
on_phone shell am instrument -w -r \
    -e runnerBuilder de.mannodermaus.junit5.AndroidJUnit5Builder \
    -e class org.jebol.android.timings.AndroidTimingsTest \
    org.jebol.android.endtoend/androidx.test.runner.AndroidJUnitRunner > build/phone-timings-instrument.log 2>&1 || true
if grep -Eq "FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2" build/phone-timings-instrument.log; then
    say "  a timing test failed; build/phone-timings-instrument.log says how"
    grep -A5 "stack=" build/phone-timings-instrument.log | head -20 >> "$report"
fi
on_phone shell cat "$measured_on_phone" | tr -d '\r' | sed 's/^/  /' | tee -a "$report"

if $with_tests; then
    say ""
    say "== androidEndToEndTests on the phone =="
    if ./gradlew -p android androidEndToEndTests "-Pdevice=$serial" > build/phone-end-to-end.log 2>&1; then
        say "  passed"
    else
        say "  failed; build/phone-end-to-end.log has it all"
        grep -E "^FAILED|tests, " build/phone-end-to-end.log | tee -a "$report" || true
    fi
fi

if $profile; then
    say ""
    say "== a profile of a drag =="
    on_phone shell am start -n org.jebol.android/.JebolActivity >/dev/null
    echo "Recording for ten seconds: drag the slider back and forth now."
    on_phone shell simpleperf record --app org.jebol.android -g --duration 10 -o /data/local/tmp/jebol-drag.data \
        > build/phone-simpleperf.log 2>&1 || true
    on_phone pull /data/local/tmp/jebol-drag.data build/phone-drag.perf.data >/dev/null && \
        say "  build/phone-drag.perf.data" || say "  simpleperf did not record; build/phone-simpleperf.log says why"
fi

say ""
say "The app is installed: open JEBOL on the phone and use it."
say "Paste $report back, with whether it felt snappy."
