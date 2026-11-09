#!/bin/sh
set -u

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../../../../.." && pwd)"
jar="$root/build/libs/jebol-3.22.5.jar"

hosts="xn--mnchen-3ya.de münchen.de bücher.de
expired.badssl.com wrong.host.badssl.com self-signed.badssl.com untrusted-root.badssl.com
incomplete-chain.badssl.com extended-validation.badssl.com superfish.badssl.com
sha1-intermediate.badssl.com invalid-expected-sct.badssl.com
dh1024.badssl.com dh2048.badssl.com static-rsa.badssl.com
client.badssl.com client-cert-missing.badssl.com
rsa8192.badssl.com ecc256.badssl.com ecc384.badssl.com revoked.badssl.com
tls-v1-0.badssl.com:1010 tls-v1-1.badssl.com:1011 tls-v1-2.badssl.com:1012
rc4.badssl.com 3des.badssl.com null.badssl.com"

answer() {
    printed=$(timeout 60 "$@" 2>&1 | grep -- '->' | tail -1)
    status=$?
    echo "${printed:-$host -> no answer in 60 seconds, or the process died}"
}

echo "# $(date -u +%Y-%m-%dT%H:%MZ)"
echo
echo "## r3-head"
for host in $hosts; do
    answer "$root/r3-head" -s "$here/live-sweep.r3" "https://$host/"
done
echo
echo "## jebol $(git -C "$root" rev-parse --short HEAD)"
for host in $hosts; do
    answer java -jar "$jar" "$here/live-sweep.r3" "https://$host/"
done
echo
echo "## openssl $(openssl version | cut -d' ' -f2), the last verify error, as s_client reports it"
for host in $hosts; do
    case $host in *:*) address=$host; name=${host%:*} ;; *) address=$host:443; name=$host ;; esac
    ascii=$(python3 -I -c 'import sys; print(sys.argv[1].encode("idna").decode())' "$name")
    verdict=$(echo | timeout 30 openssl s_client -connect "$ascii:${address##*:}" \
        -servername "$ascii" -verify_hostname "$ascii" 2>&1 \
        | grep -E 'Verify return code|alert number|errno' | tail -1 | sed 's/^ *//')
    echo "$host -> ${verdict:-no answer}"
done
