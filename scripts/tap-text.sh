#!/usr/bin/env bash
# Taps the on-screen element whose accessibility text contains the argument (Compose exposes text
# via semantics). Useful for scripted demos and for driving the head unit from a laptop.
# Usage: scripts/tap-text.sh "여행 종료"
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
NEEDLE=$1
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | python3 -c '
import re, sys
needle = sys.argv[1]
xml = sys.stdin.read()
for node in re.finditer(r"<node [^>]*>", xml):
    n = node.group(0)
    text = re.search(r"text=\"([^\"]*)\"", n)
    desc = re.search(r"content-desc=\"([^\"]*)\"", n)
    label = (text.group(1) if text else "") + " " + (desc.group(1) if desc else "")
    if needle in label:
        b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", n)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
print("NOT_FOUND", file=sys.stderr)
sys.exit(1)
' "$NEEDLE" | { read -r X Y; echo "tap '$NEEDLE' at $X,$Y"; adb shell input tap "$X" "$Y"; }
