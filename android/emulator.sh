#!/usr/bin/env bash
# Boots, or shuts down, the emulator androidTests runs the device suites on.
#
#   android/emulator.sh start    create the AVD if it is missing, boot it with
#                                no window, and wait until Android says it has
#                                finished booting
#   android/emulator.sh stop     shut it down
#
# An x86_64 API 35 Google APIs image under KVM, on a port of its own so it never
# takes over an emulator someone is using. scripts/setup-machine.sh installs the
# image. Gradle Managed Devices would do this, but refused the image sdkmanager
# installs and stalled fetching its own (decision 73).

set -euo pipefail

sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
avd=jebol-api35
port=5584
serial="emulator-$port"
image="system-images;android-35;google_apis;x86_64"
adb="$sdk/platform-tools/adb"
log="${TMPDIR:-/tmp}/jebol-emulator.log"
seconds_a_boot_may_take=300

booted() {
    [ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]
}

case "${1:-}" in
start)
    if ! "$sdk/cmdline-tools/latest/bin/avdmanager" list avd -c | grep -qx "$avd"; then
        echo no | "$sdk/cmdline-tools/latest/bin/avdmanager" create avd -n "$avd" -k "$image" -d pixel_6 --force
    fi
    if booted; then
        echo "$serial is already up"
        exit 0
    fi
    nohup "$sdk/emulator/emulator" -avd "$avd" -port "$port" -no-window -no-audio -no-boot-anim \
        -no-snapshot -gpu swiftshader_indirect > "$log" 2>&1 &
    timeout "$seconds_a_boot_may_take" "$adb" -s "$serial" wait-for-device
    waited=0
    until booted; do
        if [ "$waited" -ge "$seconds_a_boot_may_take" ]; then
            echo "$serial did not finish booting in ${seconds_a_boot_may_take}s; its log is $log" >&2
            exit 1
        fi
        sleep 2
        waited=$((waited + 2))
    done
    echo "$serial booted"
    ;;
stop)
    "$adb" -s "$serial" emu kill >/dev/null 2>&1 || true
    ;;
*)
    echo "usage: android/emulator.sh start|stop" >&2
    exit 2
    ;;
esac
