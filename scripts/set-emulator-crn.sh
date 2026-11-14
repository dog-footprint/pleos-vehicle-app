#!/usr/bin/env bash
# Injects the test vehicle CRN from .env into a Pleos target, then reboots it.
# Needs a rootable target (devbox / userdebug image). The stock Pleos Connect v2.1.0 AVD is a
# `user` build with no root, and per the Pleos docs it does not need a CRN anyway.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

CRN=$(grep -E '^CRN=' .env | head -1 | cut -d= -f2- | tr -d '"[:space:]')
[[ -n $CRN ]] || { echo "CRN is not set in .env"; exit 1; }

adb root >/dev/null
adb wait-for-device
adb shell su 0 "echo 'propId: 554696961 areaId: 0 values: ${CRN}' > /data/vendor/vsomeip/vhal_fifo"
adb reboot
adb wait-for-device
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do sleep 3; done
echo "CRN set, emulator rebooted."
