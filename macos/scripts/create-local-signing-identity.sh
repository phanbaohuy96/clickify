#!/bin/zsh
# Creates a self-signed code-signing certificate, reusable forever, and loads it into the login keychain.
#
# Why it is needed: ad-hoc signing (`codesign --sign -`) produces a designated requirement of `cdhash H"…"`,
# and the cdhash changes with **every build**. So every reinstall invalidates Accessibility and Screen
# Recording and they have to be re-enabled in System Settings — a huge waste of time mid-testing.
#
# Signing with a stable certificate produces a designated requirement of
# `identifier "com.pbh.clickify" and certificate leaf = H"…"`, which does not depend on the build. Grant
# the permission once and you are done.
#
# This certificate is **for this machine only**. It is not a Developer ID, cannot be distributed, and
# makes the app no more trustworthy to anyone else.

set -euo pipefail

name="Clickify Local Signing"

if security find-identity -p codesigning | grep -q "$name"; then
    printf '%s\n' "Already present: $name"
    exit 0
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cat > "$work/cert.cnf" <<CNF
[req]
distinguished_name = dn
x509_extensions = v3
prompt = no
[dn]
CN = $name
[v3]
basicConstraints = critical,CA:false
keyUsage = critical,digitalSignature
extendedKeyUsage = critical,codeSigning
CNF

openssl req -x509 -newkey rsa:2048 -keyout "$work/key.pem" -out "$work/cert.pem" \
    -days 3650 -nodes -config "$work/cert.cnf" >/dev/null 2>&1

openssl pkcs12 -export -out "$work/bundle.p12" -inkey "$work/key.pem" -in "$work/cert.pem" \
    -passout pass:clickify -name "$name" >/dev/null 2>&1

security import "$work/bundle.p12" -k "$HOME/Library/Keychains/login.keychain-db" \
    -P clickify -T /usr/bin/codesign -T /usr/bin/security

printf '%s\n' "Created $name."
printf '%s\n' "Run ./scripts/install.sh, then grant Accessibility + Screen Recording ONE more time."
printf '%s\n' "From the next reinstall on, the permissions will survive."
