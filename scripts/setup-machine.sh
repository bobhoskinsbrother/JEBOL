#!/usr/bin/env bash
# Installs what `./gradlew allTests` needs on a fresh Ubuntu machine.
#
#   scripts/setup-machine.sh
#
# Safe to run again: each step checks before it installs. It asks for sudo.
#
#   Java 25           openjdk-25-jdk, for Gradle and the toolchain the build asks for
#   allium            cargo install allium-cli, for checkSpec (needs rustup and a C linker)
#   python3           scripts/allium_report.py and the parity scripts
#   Chrome            endToEndTests, from Google's apt repository
#   Firefox           endToEndTests, from Mozilla's apt repository, never the snap
#   zsh, openssl      scripts/build-r3.sh and scripts/make-test-certificates.sh
#   Android SDK       android/ and checkCoreForAndroid, in ~/Android/Sdk: the
#                     command-line tools, platform 35, build tools 37 (d8) and
#                     36 (the Android Gradle Plugin's own), the emulator and an
#                     x86_64 API 35 image (goal 2)
#
# A Firefox snap is removed, and its profile in ~/snap/firefox with it.
#
# Installing the Android SDK accepts Google's Android SDK licence on your
# behalf, as Android Studio asks you to; read it at
# https://developer.android.com/studio/terms before running this.

set -euo pipefail

cd "$(dirname "$0")/.."

step() { printf '\n== %s ==\n' "$*"; }
have() { command -v "$1" >/dev/null 2>&1; }

if [ ! -r /etc/os-release ] || ! grep -q '^ID=ubuntu' /etc/os-release; then
    echo "This script installs with apt on Ubuntu; on anything else read the README's Building section." >&2
    exit 1
fi

step "apt packages"
sudo apt-get update
sudo apt-get install -y \
    openjdk-25-jdk \
    build-essential pkg-config libssl-dev \
    python3 git curl wget gnupg ca-certificates file unzip \
    zsh openssl \
    rustup

step "Rust toolchain"
if ! rustup toolchain list | grep -q stable; then
    rustup default stable
else
    rustup update stable
fi

step "allium"
export PATH="$HOME/.cargo/bin:$PATH"
if have allium; then
    echo "allium is already installed: $(allium --version 2>&1 | head -1)"
else
    cargo install allium-cli
fi

step "Google Chrome"
if have google-chrome; then
    echo "Chrome is already installed: $(google-chrome --version)"
else
    sudo install -d -m 0755 /etc/apt/keyrings
    wget -qO- https://dl.google.com/linux/linux_signing_key.pub \
        | sudo gpg --dearmor --yes -o /etc/apt/keyrings/google-chrome.gpg
    echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/google-chrome.gpg] https://dl.google.com/linux/chrome/deb/ stable main" \
        | sudo tee /etc/apt/sources.list.d/google-chrome.list >/dev/null
    sudo apt-get update
    sudo apt-get install -y google-chrome-stable
fi

step "Firefox from Mozilla"
if snap list firefox >/dev/null 2>&1; then
    echo "Removing the Firefox snap, which the Firefox driver refuses (see CLAUDE.md)."
    sudo snap remove firefox
fi
if [ ! -f /etc/apt/sources.list.d/mozilla.list ]; then
    sudo install -d -m 0755 /etc/apt/keyrings
    wget -qO- https://packages.mozilla.org/apt/repo-signing-key.gpg \
        | sudo tee /etc/apt/keyrings/packages.mozilla.org.asc >/dev/null
    echo "deb [signed-by=/etc/apt/keyrings/packages.mozilla.org.asc] https://packages.mozilla.org/apt mozilla main" \
        | sudo tee /etc/apt/sources.list.d/mozilla.list >/dev/null
fi
sudo tee /etc/apt/preferences.d/mozilla >/dev/null <<'PIN'
Package: *
Pin: origin packages.mozilla.org
Pin-Priority: 1000
PIN
sudo tee /etc/apt/preferences.d/no-ubuntu-firefox >/dev/null <<'PIN'
Package: firefox*
Pin: release o=Ubuntu
Pin-Priority: -1
PIN
sudo apt-get update
sudo apt-get install -y --allow-downgrades firefox

step "Android SDK"
# The command-line tools are pinned and checked against the digest Google
# publishes for them in https://dl.google.com/android/repository/repository2-3.xml;
# sdkmanager fetches and checks everything after them itself.
android_sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
command_line_tools=commandlinetools-linux-16111833_latest.zip
command_line_tools_sha1=e025545c62a8e64c7559119566a569fb1dec5f60
sdkmanager="$android_sdk/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$sdkmanager" ]; then
    echo "The Android command-line tools are already in $android_sdk"
else
    fetched=$(mktemp -d)
    wget -q -O "$fetched/$command_line_tools" "https://dl.google.com/android/repository/$command_line_tools"
    echo "$command_line_tools_sha1  $fetched/$command_line_tools" | sha1sum -c -
    mkdir -p "$android_sdk/cmdline-tools"
    unzip -q -o "$fetched/$command_line_tools" -d "$fetched"
    rm -rf "$android_sdk/cmdline-tools/latest"
    mv "$fetched/cmdline-tools" "$android_sdk/cmdline-tools/latest"
    rm -rf "$fetched"
fi
echo "Accepting the Android SDK licence, as the header of this script says."
yes | "$sdkmanager" --sdk_root="$android_sdk" --licenses >/dev/null
# sdkmanager can stop at a prompt of its own even with every licence accepted,
# and with nothing on its input it waits there for good.
yes | "$sdkmanager" --sdk_root="$android_sdk" \
    "platforms;android-35" "build-tools;37.0.0" "build-tools;36.0.0" "platform-tools" "emulator" \
    "system-images;android-35;google_apis;x86_64"
if [ ! -w /dev/kvm ]; then
    echo "Adding $USER to the kvm group, so the emulator runs at full speed; log in again to pick it up."
    sudo usermod -aG kvm "$USER"
fi

step "PATH"
if ! grep -qs '.cargo/bin' "$HOME/.bashrc" "$HOME/.profile"; then
    echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> "$HOME/.bashrc"
    echo "Added ~/.cargo/bin to PATH in ~/.bashrc; open a new shell to pick it up."
fi
if ! grep -qs 'ANDROID_HOME' "$HOME/.bashrc" "$HOME/.profile"; then
    echo "export ANDROID_HOME=\"$android_sdk\"" >> "$HOME/.bashrc"
    echo "Added ANDROID_HOME to ~/.bashrc; open a new shell to pick it up."
fi

step "Checking"
failed=false
check() {
    local what=$1; shift
    if "$@" >/dev/null 2>&1; then
        printf '  ok    %s\n' "$what"
    else
        printf '  FAIL  %s\n' "$what"
        failed=true
    fi
}
check "java 25 on the PATH"         sh -c 'java -version 2>&1 | grep -q "version \"25"'
check "allium"                      allium --version
check "python3"                     python3 --version
check "google-chrome"               google-chrome --version
check "firefox is an ELF, not a script" sh -c 'file -L "$(command -v firefox)" | grep -q ELF'
check "Ubuntu's firefox placeholder is never installed" sh -c 'apt-cache policy firefox | grep -q "1snap1.* -1"'
check "Android platform 35"         test -f "$android_sdk/platforms/android-35/android.jar"
check "Android build tools 37"      test -x "$android_sdk/build-tools/37.0.0/d8"
check "the Android emulator"        "$android_sdk/emulator/emulator" -version
check "an x86_64 API 35 image"      test -f "$android_sdk/system-images/android-35/google_apis/x86_64/system.img"
check "/dev/kvm can be opened"      test -w /dev/kvm
check "gradle wrapper starts"       ./gradlew --version
check "spec/ passes allium"         ./gradlew checkSpec

if $failed; then
    echo
    echo "Something above failed; the build will name it too." >&2
    exit 1
fi
echo
echo "Ready: ./gradlew allTests"
