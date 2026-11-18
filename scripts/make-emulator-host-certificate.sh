#!/bin/sh
# The root and the leaf the Android end-to-end suite's TLS server on the host
# presents to the app at 10.0.2.2 (goal 2c step 6). A set of its own, so the
# core's fixtures from make-test-certificates.sh stay as they are. The root is
# handed to a debug build for one launch only; nothing trusts it otherwise.
set -eu

endtoend="$(cd "$(dirname "$0")/.." && pwd)/android/endtoend"
carried="$endtoend/src/main/resources/emulator-host"
served="$endtoend/host-server"
mkdir -p "$carried" "$served"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out root-key.pem 2>/dev/null
openssl req -new -key root-key.pem -subj "/CN=emulator-test-root" -out root.csr
printf 'basicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\n' > root.ext
openssl x509 -req -in root.csr -key root-key.pem -extfile root.ext \
    -not_before 20260101000000Z -not_after 20460101000000Z -out emulator-test-root.pem

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out host-key.pem 2>/dev/null
openssl req -new -key host-key.pem -subj "/CN=10.0.2.2" -out host.csr
printf 'basicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\nsubjectAltName=IP:10.0.2.2\n' > host.ext
openssl x509 -req -in host.csr -CA emulator-test-root.pem -CAkey root-key.pem -CAcreateserial \
    -extfile host.ext -not_before 20260101000000Z -not_after 20360101000000Z -out emulator-host.pem

openssl pkcs8 -topk8 -nocrypt -in host-key.pem -out emulator-host-key.pem
cp emulator-test-root.pem "$carried/"
cp emulator-host.pem emulator-host-key.pem "$served/"
