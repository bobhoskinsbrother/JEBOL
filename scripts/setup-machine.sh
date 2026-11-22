#!/usr/bin/env bash
# Installs what `./gradlew allTests` needs on a fresh Ubuntu machine, and on a
# Mac the Android SDK alone.
#
#   scripts/setup-machine.sh
#
# Safe to run again: each step checks before it installs. On Ubuntu it asks
# for sudo; on a Mac it needs none.
#
# On macOS it installs the Android SDK into ~/Library/Android/sdk, where
# Android Studio puts it: the command-line tools for the Mac's processor, the
# same platform, build tools and emulator as on Ubuntu, and the API 35 and 37
# images the processor runs -- arm64-v8a on Apple Silicon, x86_64 on Intel -- then
# adds ANDROID_HOME to ~/.zprofile. Everything else allTests needs on a Mac is
# the README's Building section.
#
#   Java 21           openjdk-21-jdk, for Gradle and the core's toolchain
#   Java 25           openjdk-25-jdk, the desktop's toolchain, the tests' and jpackage
#   fakeroot          jpackage's .deb (goal 2e)
#   allium            cargo install allium-cli, pinned, for checkSpec (needs rustup and a C linker);
#                     a newer one checks spec/ by rules the spec was not written to
#   python3           scripts/allium_report.py and the parity scripts
#   Chrome            endToEndTests, from Google's apt repository
#   Firefox           endToEndTests, from Mozilla's apt repository, never the snap
#   zsh, openssl      scripts/build-r3.sh and scripts/make-test-certificates.sh
#   Android SDK       android/ and checkCoreForAndroid, in ~/Android/Sdk: the
#                     command-line tools, platform 35, build tools 37 (d8) and
#                     36 (the Android Gradle Plugin's own), the emulator and an
#                     x86_64 API 35 and API 37 images (goal 2)
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

# The command-line tools are pinned and checked against the digest Google
# publishes for them in https://dl.google.com/android/repository/repository2-3.xml;
# sdkmanager fetches and checks everything after them itself.
install_android_sdk() {
    local android_sdk=$1 command_line_tools=$2 command_line_tools_sha1=$3 image=$4
    local sdkmanager="$android_sdk/cmdline-tools/latest/bin/sdkmanager"
    if [ -x "$sdkmanager" ]; then
        echo "The Android command-line tools are already in $android_sdk"
    else
        local fetched
        fetched=$(mktemp -d)
        curl -fsSL -o "$fetched/$command_line_tools" "https://dl.google.com/android/repository/$command_line_tools"
        if have sha1sum; then
            echo "$command_line_tools_sha1  $fetched/$command_line_tools" | sha1sum -c -
        else
            echo "$command_line_tools_sha1  $fetched/$command_line_tools" | shasum -a 1 -c -
        fi
        mkdir -p "$android_sdk/cmdline-tools"
        unzip -q -o "$fetched/$command_line_tools" -d "$fetched"
        rm -rf "$android_sdk/cmdline-tools/latest"
        mv "$fetched/cmdline-tools" "$android_sdk/cmdline-tools/latest"
        rm -rf "$fetched"
    fi
    echo "Accepting the Android SDK licence, as the header of this script says."
    # yes dies of SIGPIPE when sdkmanager stops reading, which pipefail would
    # count as the pipeline failing; only sdkmanager's own status counts.
    { yes || true; } | "$sdkmanager" --sdk_root="$android_sdk" --licenses >/dev/null
    # sdkmanager can stop at a prompt of its own even with every licence accepted,
    # and with nothing on its input it waits there for good.
    { yes || true; } | "$sdkmanager" --sdk_root="$android_sdk" \
        "platforms;android-35" "build-tools;37.0.0" "build-tools;36.0.0" "platform-tools" "emulator" \
        "$image" "${image/android-35/android-37.0}"
}

check_android_sdk() {
    local android_sdk=$1 image_dir=$2
    check "Android platform 35"         test -f "$android_sdk/platforms/android-35/android.jar"
    check "Android build tools 37"      test -x "$android_sdk/build-tools/37.0.0/d8"
    check "the Android emulator"        "$android_sdk/emulator/emulator" -version
    check "an API 35 image ($image_dir)" test -f "$android_sdk/system-images/android-35/google_apis/$image_dir/system.img"
    check "an API 37 image ($image_dir)" test -f "$android_sdk/system-images/android-37.0/google_apis/$image_dir/system.img"
}

if [ "$(uname -s)" = Darwin ]; then
    android_sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
    case "$(uname -m)" in
    arm64)
        command_line_tools=commandlinetools-mac_arm64-16111833_latest.zip
        command_line_tools_sha1=ad03dc49bfacfd52c110b14104ea548b8a07e830
        image_dir=arm64-v8a
        ;;
    *)
        command_line_tools=commandlinetools-mac_x86_64-16111833_latest.zip
        command_line_tools_sha1=112cf9618794a997ff273537d55bee02c22abffe
        image_dir=x86_64
        ;;
    esac

    step "Android SDK"
    install_android_sdk "$android_sdk" "$command_line_tools" "$command_line_tools_sha1" \
        "system-images;android-35;google_apis;$image_dir"

    step "PATH"
    if ! grep -qs 'ANDROID_HOME' "$HOME/.zprofile"; then
        echo "export ANDROID_HOME=\"$android_sdk\"" >> "$HOME/.zprofile"
        echo "Added ANDROID_HOME to ~/.zprofile; open a new shell to pick it up."
    fi

    step "Checking"
    check "java on the PATH"            java -version
    check_android_sdk "$android_sdk" "$image_dir"
    check "gradle wrapper starts"       ./gradlew --version
    if $failed; then
        echo
        echo "Something above failed; the build will name it too." >&2
        exit 1
    fi
    echo
    echo "Ready: ./gradlew -p android androidTests -Pemulators=1"
    exit 0
fi

if [ ! -r /etc/os-release ] || ! grep -q '^ID=ubuntu' /etc/os-release; then
    echo "This script installs with apt on Ubuntu; on anything else read the README's Building section." >&2
    exit 1
fi

step "apt packages"
sudo apt-get update
sudo apt-get install -y \
    openjdk-21-jdk openjdk-25-jdk fakeroot \
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
allium_version=3.6.1
if have allium && allium --version 2>&1 | grep -q "^allium $allium_version "; then
    echo "allium $allium_version is already installed"
else
    cargo install allium-cli --version "$allium_version" --locked --force
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
android_sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
install_android_sdk "$android_sdk" commandlinetools-linux-16111833_latest.zip \
    e025545c62a8e64c7559119566a569fb1dec5f60 "system-images;android-35;google_apis;x86_64"
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
check "java 21 on the PATH"         sh -c 'java -version 2>&1 | grep -q "version \"21"'
check "allium"                      allium --version
check "python3"                     python3 --version
check "google-chrome"               google-chrome --version
check "firefox is an ELF, not a script" sh -c 'file -L "$(command -v firefox)" | grep -q ELF'
check "Ubuntu's firefox placeholder is never installed" sh -c 'apt-cache policy firefox | grep -q "1snap1.* -1"'
check_android_sdk "$android_sdk" x86_64
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
