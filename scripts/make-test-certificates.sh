#!/bin/sh
set -eu

out="$(cd "$(dirname "$0")/.." && pwd)/src/testFixtures/resources/org/jebol/tls"
mkdir -p "$out"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"

authority_from=20260101000000Z
authority_until=20460101000000Z
leaf_from=20260101000000Z
leaf_until=20360101000000Z

rsa_key() { openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$1" 2>/dev/null; }
ec_key() { openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$1" 2>/dev/null; }
ed25519_key() { openssl genpkey -algorithm ED25519 -out "$1" 2>/dev/null; }

authority() {
    name=$1; key=$2; issuer=$3; issuer_key=$4
    openssl req -new -key "$key" -subj "/CN=$name" -out "$name.csr"
    printf 'basicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\n' > "$name.ext"
    if [ "$issuer" = self ]; then
        openssl x509 -req -in "$name.csr" -key "$key" -extfile "$name.ext" \
            -not_before $authority_from -not_after $authority_until -out "$name.pem"
    else
        openssl x509 -req -in "$name.csr" -CA "$issuer" -CAkey "$issuer_key" -CAcreateserial \
            -extfile "$name.ext" -not_before $authority_from -not_after $authority_until -out "$name.pem"
    fi
}

leaf() {
    name=$1; key=$2; names=$3; issuer=$4; issuer_key=$5; from=$6; until=$7
    openssl req -new -key "$key" -subj "/CN=localhost" -out "$name.csr"
    {
        echo "basicConstraints=critical,CA:FALSE"
        echo "extendedKeyUsage=serverAuth"
        [ -n "$names" ] && echo "subjectAltName=$names"
    } > "$name.ext"
    if [ "$issuer" = self ]; then
        openssl x509 -req -in "$name.csr" -key "$key" -extfile "$name.ext" \
            -not_before "$from" -not_after "$until" -out "$name.pem"
    else
        openssl x509 -req -in "$name.csr" -CA "$issuer" -CAkey "$issuer_key" -CAcreateserial \
            -extfile "$name.ext" -not_before "$from" -not_after "$until" -out "$name.pem"
    fi
}

rsa_key root-key.pem
authority test-root root-key.pem self -
rsa_key intermediate-key.pem
authority test-intermediate intermediate-key.pem test-root.pem root-key.pem
rsa_key stranger-root-key.pem
authority stranger-root stranger-root-key.pem self -
openssl req -new -key root-key.pem -subj "/CN=test-root" -out cross.csr
openssl x509 -req -in cross.csr -CA stranger-root.pem -CAkey stranger-root-key.pem -CAcreateserial \
    -extfile test-root.ext -not_before $authority_from -not_after $authority_until -out test-root-cross-signed.pem

rsa_key localhost-rsa-key.pem
leaf localhost-rsa localhost-rsa-key.pem "DNS:localhost,IP:127.0.0.1" \
    test-intermediate.pem intermediate-key.pem $leaf_from $leaf_until
ec_key localhost-ec-key.pem
leaf localhost-ec localhost-ec-key.pem "DNS:localhost,IP:127.0.0.1" \
    test-intermediate.pem intermediate-key.pem $leaf_from $leaf_until
ed25519_key localhost-ed25519-key.pem
leaf localhost-ed25519 localhost-ed25519-key.pem "DNS:localhost,IP:127.0.0.1" \
    test-intermediate.pem intermediate-key.pem $leaf_from $leaf_until
leaf other-host localhost-rsa-key.pem "DNS:other.test" \
    test-intermediate.pem intermediate-key.pem $leaf_from $leaf_until
leaf common-name-only localhost-rsa-key.pem "" \
    test-intermediate.pem intermediate-key.pem $leaf_from $leaf_until
leaf self-signed localhost-rsa-key.pem "DNS:localhost,IP:127.0.0.1" \
    self - $leaf_from $leaf_until
leaf from-a-stranger localhost-rsa-key.pem "DNS:localhost,IP:127.0.0.1" \
    stranger-root.pem stranger-root-key.pem $leaf_from $leaf_until
leaf one-day localhost-rsa-key.pem "DNS:localhost,IP:127.0.0.1" \
    test-intermediate.pem intermediate-key.pem 20270101000000Z 20270102000000Z

rsa_key impostor-rsa-key.pem
ec_key impostor-ec-key.pem

for each in test-root test-root-cross-signed test-intermediate stranger-root localhost-rsa localhost-ec localhost-ed25519 \
        other-host common-name-only self-signed from-a-stranger one-day; do
    cp "$each.pem" "$out/$each.pem"
done
for each in localhost-rsa-key localhost-ec-key localhost-ed25519-key impostor-rsa-key impostor-ec-key; do
    cp "$each.pem" "$out/$each.pem"
done
