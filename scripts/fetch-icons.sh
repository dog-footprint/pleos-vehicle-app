#!/usr/bin/env bash
# Downloads Material Symbols (Outlined, 48px) and converts them to Android vector drawables
# res/drawable/ic_<name>.xml — one consistent thin-line set, matching Pleos' 48px line icons.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/src/main/res/drawable
TMP=$(mktemp -d)
ICONS=(thermostat footprint pets directions_car door_open sensor_window speed warning info settings history person
  mode_fan ac_unit sunny check_circle close chevron_right add edit delete play_arrow stop mic schedule
  battery_charging_full local_parking airline_seat_recline_normal lock volume_up home list navigation
  local_cafe route graphic_eq monitor_weight error cancel sentiment_satisfied sentiment_neutral sentiment_dissatisfied
  pause health_and_safety timer)
ok=0; fail=()
for n in "${ICONS[@]}"; do
  url="https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/$n/default/48px.svg"
  if curl -sf -o "$TMP/$n.svg" "$url"; then
    python3 - "$TMP/$n.svg" "$OUT/ic_$n.xml" <<'PY'
import re, sys
svg = open(sys.argv[1]).read()
paths = re.findall(r'<path[^>]*\sd="([^"]+)"', svg)
vb = re.search(r'viewBox="([^"]+)"', svg).group(1).split()
w, h = float(vb[2]), float(vb[3]); ty = -float(vb[1])
body = "".join(f'    <path android:fillColor="@android:color/white" android:pathData="{d}" />\n' for d in paths)
open(sys.argv[2], "w").write(
  '<?xml version="1.0" encoding="utf-8"?>\n'
  '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
  '    android:width="48dp" android:height="48dp"\n'
  f'    android:viewportWidth="{w:g}" android:viewportHeight="{h:g}"\n'
  '    android:tint="?attr/colorControlNormal">\n'
  f'  <group android:translateY="{ty:g}">\n{body}  </group>\n</vector>\n')
PY
    ok=$((ok+1))
  else fail+=("$n"); fi
done
echo "converted $ok icons; missing: ${fail[*]:-none}"
