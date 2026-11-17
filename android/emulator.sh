#!/usr/bin/env bash
# Boots, or shuts down, the emulators androidTests runs the device suites on.
#
#   android/emulator.sh start [N]   create the AVD if it is missing, boot N
#                                   read-only instances of it (one by default)
#                                   with no window, and wait until Android says
#                                   each has finished booting
#   android/emulator.sh stop [N]    shut them down
#   android/emulator.sh serials [N] print the instances' serials, one a line
#
# A Pixel Tablet -- 2560 by 1600 pixels at 320 dpi, so 1280 by 800 dp, the size
# goal 2b proves the examples on (decision 75) -- running the API 35 Google APIs
# image for the machine's processor: x86_64 under KVM on
# Linux and on an Intel Mac, arm64-v8a under the Mac's own hypervisor on Apple
# Silicon. The SDK is ANDROID_HOME, else where Android Studio puts it on each
# system (~/Android/Sdk, ~/Library/Android/sdk). The instances take the ports
# from 5584 up, two apart, so they never take over an emulator someone is
# using; read-only lets one AVD run several times at once. The suites are split
# across them in shards (decision 73). scripts/setup-machine.sh installs the
# image. Gradle Managed Devices would do this, but refused the image sdkmanager
# installs and stalled fetching its own (decision 73).

set -euo pipefail

if [ "$(uname -s)" = Darwin ]; then
    sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
else
    sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
fi
case "$(uname -m)" in
arm64 | aarch64) abi=arm64-v8a ;;
*) abi=x86_64 ;;
esac
avd="jebol-api35-tablet-$abi"
profile=pixel_tablet
first_port=5584
image="system-images;android-35;google_apis;$abi"
adb="$sdk/platform-tools/adb"
seconds_a_boot_may_take=420
megabytes_each=1536
count="${2:-1}"

serial_of() {
    echo "emulator-$((first_port + 2 * $1))"
}

booted() {
    [ "$("$adb" -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]
}

wait_for_boot() {
    local serial=$1 log=$2 waited=0
    until booted "$serial"; do
        if [ "$waited" -ge "$seconds_a_boot_may_take" ]; then
            echo "$serial did not finish booting in ${seconds_a_boot_may_take}s; its log is $log" >&2
            return 1
        fi
        sleep 2
        waited=$((waited + 2))
    done
    echo "$serial booted"
}

case "${1:-}" in
start)
    if ! "$sdk/cmdline-tools/latest/bin/avdmanager" list avd -c | grep -qx "$avd"; then
        echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd -n "$avd" -k "$image" -d "$profile" --force
    fi
    for ((at = 0; at < count; at++)); do
        serial=$(serial_of "$at")
        if booted "$serial"; then
            echo "$serial is already up"
            continue
        fi
        nohup "$sdk/emulator/emulator" -avd "$avd" -port "$((first_port + 2 * at))" -read-only -no-window \
            -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory "$megabytes_each" \
            > "${TMPDIR:-/tmp}/jebol-emulator-$at.log" 2>&1 &
    done
    for ((at = 0; at < count; at++)); do
        wait_for_boot "$(serial_of "$at")" "${TMPDIR:-/tmp}/jebol-emulator-$at.log"
    done
    ;;
stop)
    for ((at = 0; at < count; at++)); do
        "$adb" -s "$(serial_of "$at")" emu kill >/dev/null 2>&1 || true
    done
    ;;
serials)
    for ((at = 0; at < count; at++)); do
        serial_of "$at"
    done
    ;;
*)
    echo "usage: android/emulator.sh start|stop|serials [N]" >&2
    exit 2
    ;;
esac
