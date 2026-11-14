#!/usr/bin/env bash
# Creates the release signing key and keystore.properties (both gitignored).
# Keep the .jks and passwords somewhere safe — Pleos App Market ties the app to this key.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

STORE=release.jks
ALIAS=${1:-release}
[[ -e $STORE ]] && { echo "$STORE already exists, aborting."; exit 1; }

# Non-interactive: KEYSTORE_PASSWORD=... scripts/create-release-keystore.sh
PASS=${KEYSTORE_PASSWORD:-}
if [[ -z $PASS ]]; then read -rsp "Keystore password (min 6 chars): " PASS; echo; fi
keytool -genkeypair -v -keystore "$STORE" -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=${ALIAS}, O=HMG App Dev Challenge, C=KR"

cat > keystore.properties <<PROPS
storeFile=$STORE
storePassword=$PASS
keyAlias=$ALIAS
keyPassword=$PASS
PROPS
echo "Wrote $STORE and keystore.properties"
