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
nodes = [n.group(0) for n in re.finditer(r"<node [^>]*>", xml)]
def label(n):
    text = re.search(r"text=\"([^\"]*)\"", n); desc = re.search(r"content-desc=\"([^\"]*)\"", n)
    return (text.group(1) if text else "") + " " + (desc.group(1) if desc else "")
hits = [n for n in nodes if needle in label(n)]
# A button word can also appear in a hint sentence; Compose marks only the button clickable, so prefer that,
# then the shortest label (the exact button text) over a longer sentence.
hits.sort(key=lambda n: ("clickable=\"true\"" not in n, len(label(n))))
if hits:
    b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", hits[0])
    x1, y1, x2, y2 = map(int, b.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    sys.exit(0)
print("NOT_FOUND", file=sys.stderr)
sys.exit(1)
' "$NEEDLE" | { read -r X Y; echo "tap '$NEEDLE' at $X,$Y"; adb shell input tap "$X" "$Y"; }
