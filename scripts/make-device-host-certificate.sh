#!/bin/sh
# The root and the leaf the Android end-to-end suite's TLS server on the host
# presents to the app, at whichever address the run gives the device: 10.0.2.2
# from an emulator, localhost through adb reverse from a phone. A set of its
# own, so the core's fixtures from make-test-certificates.sh stay as they are.
# The root is handed to a debug build for one launch only; nothing trusts it
# otherwise. The two certificates are checked in. The leaf's private key never
# is: it is written to ~/.config/jebol/device-host-key.pem, goes into
# jebol-ci-config's vault as device_host_private_key, and the toolchain role
# installs it there on each machine that runs the device suites.
set -eu

endtoend="$(cd "$(dirname "$0")/.." && pwd)/android/endtoend"
carried="$endtoend/src/main/resources/device-host"
served="$endtoend/host-server"
kept="$HOME/.config/jebol"
mkdir -p "$carried" "$served" "$kept"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out root-key.pem 2>/dev/null
openssl req -new -key root-key.pem -subj "/CN=device-test-root" -out root.csr
printf 'basicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\n' > root.ext
openssl x509 -req -in root.csr -key root-key.pem -extfile root.ext \
    -not_before 20260101000000Z -not_after 20460101000000Z -out device-test-root.pem

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out host-key.pem 2>/dev/null
openssl req -new -key host-key.pem -subj "/CN=localhost" -out host.csr
printf 'basicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\nsubjectAltName=IP:10.0.2.2,IP:127.0.0.1,DNS:localhost\n' > host.ext
openssl x509 -req -in host.csr -CA device-test-root.pem -CAkey root-key.pem -CAcreateserial \
    -extfile host.ext -not_before 20260101000000Z -not_after 20360101000000Z -out device-host.pem

(umask 077 && openssl pkcs8 -topk8 -nocrypt -in host-key.pem -out "$kept/device-host-key.pem")
cp device-test-root.pem "$carried/"
cp device-host.pem "$served/"
echo "the key is in $kept/device-host-key.pem: put it in jebol-ci-config's vault as device_host_private_key"
