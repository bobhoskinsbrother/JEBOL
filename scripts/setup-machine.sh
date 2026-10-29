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
#
# A Firefox snap is removed, and its profile in ~/snap/firefox with it.

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
    python3 git curl wget gnupg ca-certificates file \
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
sudo apt-get update
sudo apt-get install -y --allow-downgrades firefox

step "PATH"
if ! grep -qs '.cargo/bin' "$HOME/.bashrc" "$HOME/.profile"; then
    echo 'export PATH="$HOME/.cargo/bin:$PATH"' >> "$HOME/.bashrc"
    echo "Added ~/.cargo/bin to PATH in ~/.bashrc; open a new shell to pick it up."
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
check "gradle wrapper starts"       ./gradlew --version
check "spec/ passes allium"         ./gradlew checkSpec

if $failed; then
    echo
    echo "Something above failed; the build will name it too." >&2
    exit 1
fi
echo
echo "Ready: ./gradlew allTests"
