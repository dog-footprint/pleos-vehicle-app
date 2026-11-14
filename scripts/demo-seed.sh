#!/usr/bin/env bash
# Replaces the app's stored data with a JSON file (debug builds only) and (re)opens the app.
# Usage: scripts/demo-seed.sh [path/to/seed.json]   (default: scripts/demo-seed.json)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
SEED=${1:-scripts/demo-seed.json}
B64=$(base64 < "$SEED" | tr -d '\n')
adb shell am start -n com.dogfootprint.app/.MainActivity --es seed_b64 "$B64" >/dev/null
echo "Seeded from $SEED"
