#!/usr/bin/env bash
# Packages examples/an-app-of-ones-own as a macOS .app with jebol-devtools,
# checks it with the Mac's own tools, opens it as Finder would, and writes what
# it found to build/mac-app-report.txt, the file to paste back. Goal 2e.
#
#   scripts/app-on-my-mac.sh
#
# Needs a JDK 21 or newer on the PATH to run Gradle; Gradle fetches the JDK 25
# jpackage comes from. Xcode's command-line tools give codesign, plutil and
# iconutil: xcode-select --install.

set -euo pipefail

cd "$(dirname "$0")/.."

if [ "$(uname -s)" != Darwin ]; then
    echo "This script is for a Mac." >&2
    exit 1
fi

report=build/mac-app-report.txt
mkdir -p build
: > "$report"
say() { printf '%s\n' "$*" | tee -a "$report"; }
check() {
    local what=$1
    shift
    if output=$("$@" 2>&1); then
        say "ok    $what"
    else
        say "FAIL  $what"
    fi
    printf '%s\n' "$output" | sed 's/^/      /' >> "$report"
}

identity=org.jebol.example.notes
example=examples/an-app-of-ones-own
app="$example/build/jebol/macos/package/Notes.app"
store="$HOME/Library/Application Support/$identity"

say "macOS $(sw_vers -productVersion) on $(uname -m), $(date)"
say "commit $(git rev-parse --short HEAD)"

./gradlew :devtools:publishDevtools --console=plain -q
(cd "$example" && ../../gradlew packageMac --console=plain) | tee -a "$report"

check "the .app is there" test -d "$app"
check "its signature verifies (ad hoc)" codesign --verify --deep --strict --verbose=2 "$app"
check "its bundle identifier is the identity" sh -c "plutil -extract CFBundleIdentifier raw '$app/Contents/Info.plist' | grep -x $identity"
check "its version is app.reb's" sh -c "plutil -extract CFBundleShortVersionString raw '$app/Contents/Info.plist' | grep -x 1.0.0"
check "its build is app.reb's" sh -c "plutil -extract CFBundleVersion raw '$app/Contents/Info.plist' | grep -x 1"
check "its category is productivity" sh -c "plutil -extract LSApplicationCategoryType raw '$app/Contents/Info.plist' | grep -x public.app-category.productivity"
iconset=$(mktemp -d)/Notes.iconset
check "iconutil reads the .icns jebol-devtools wrote" iconutil -c iconset "$app/Contents/Resources/Notes.icns" -o "$iconset"
check "the iconset holds every size" sh -c "ls '$iconset' | tee /dev/stderr | wc -l | grep -q -E '^ *(10|[1-9][0-9])$'"

rm -rf "$store"
open -n --stdout "$PWD/build/mac-app-stdout.txt" --stderr "$PWD/build/mac-app-stderr.txt" "$app"
sleep 20
check "the app is running" pgrep -f "Notes.app/Contents/MacOS/Notes"
check "it said its window is open" grep -x open build/mac-app-stdout.txt
check "it keeps a store under its identity" test -d "$store"
screencapture -x build/mac-app.png && say "a screenshot is in build/mac-app.png"
say "--- what the app printed"
cat build/mac-app-stdout.txt build/mac-app-stderr.txt >> "$report" 2>/dev/null || true
osascript -e 'quit app "Notes"' >/dev/null 2>&1 || pkill -f "Notes.app/Contents/MacOS/Notes" || true

say "--- done; paste build/mac-app-report.txt back, and attach build/mac-app.png"
