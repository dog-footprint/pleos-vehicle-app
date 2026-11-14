#!/usr/bin/env bash
# Renders the recorded demo into an MP4 with Korean caption overlays.
# Usage: scripts/make-demo-video.sh [build/demo] [docs/demo/dog-footprint-demo.mp4]
# Needs ffmpeg (libx264) and Pillow (python3 -m pip install pillow); captions use Apple SD Gothic Neo.
set -euo pipefail
cd "$(dirname "$0")/.."
IN=${1:-build/demo}
OUT=${2:-docs/demo/dog-footprint-demo.mp4}
mkdir -p "$(dirname "$OUT")" "$IN/captions"

python3 - "$IN" <<'EOF'
import json, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

indir = sys.argv[1]
data = json.load(open(f"{indir}/scenes.json"))

# Segments are concatenated; convert each scene's segment-local time to the global timeline.
n_seg = len(data["segment_ends"])
seg_dur = []
for k in range(n_seg):
    out = subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", f"{indir}/demo-{k}.webm"])
    seg_dur.append(float(out))
offsets = [sum(seg_dur[:k]) for k in range(n_seg)]
scenes = []
for s in data["scenes"]:
    k = s["segment"]
    scenes.append({**s, "start": round(offsets[k] + min(s["start"], seg_dur[k]), 2)})
total = sum(seg_dur)
# Captions live in a strip BELOW the video (output 1920x1200) so they never cover the app's buttons.
W, H = 1920, 120
font = ImageFont.truetype("/System/Library/Fonts/AppleSDGothicNeo.ttc", 36)
MAX_W = W - 2 * 72

def wrap_px(text):
    lines, cur = [], ""
    for word in text.split(" "):
        cand = (cur + " " + word).strip()
        if font.getlength(cand) <= MAX_W or not cur:
            cur = cand
        else:
            lines.append(cur); cur = word
    if cur: lines.append(cur)
    return lines
filters = []
for i, s in enumerate(scenes):
    end = scenes[i + 1]["start"] if i + 1 < len(scenes) else total
    img = Image.new("RGBA", (W, H), (19, 20, 23, 255))
    d = ImageDraw.Draw(img)
    lines = wrap_px(s["caption"])[:2]
    y = (H - 48 * len(lines)) // 2
    for line in lines:
        d.text((72, y), line, font=font, fill=(255, 255, 255, 255))
        y += 48
    path = f"{indir}/captions/{i:02d}.png"
    img.save(path)
    filters.append((path, s["start"], end))

# ffmpeg graph: concat the segments (video+audio), then chain one overlay per caption window.
seg_inputs = " ".join(f"-i {indir}/demo-{k}.webm" for k in range(n_seg))
cap_inputs = " ".join(f"-i {p}" for p, _, _ in filters)
# The emulator's capture is quiet (TTS peaks ≈ -27 dB), so normalise loudness for playback on a laptop/projector.
chain = ["".join(f"[{k}:v][{k}:a]" for k in range(n_seg)) + f"concat=n={n_seg}:v=1:a=1[cv][ca]", "[ca]loudnorm=I=-16:TP=-1.5:LRA=11[aout]",
         "[cv]scale=1920:1080:flags=lanczos,pad=1920:1200:0:0:color=#131417[base]"]
prev = "[base]"
for n, (_, a, b) in enumerate(filters):
    idx = n_seg + n
    out = f"[v{n}]"
    chain.append(f"{prev}[{idx}:v]overlay=0:1080:enable='between(t,{a},{b})'{out}")
    prev = out
chain.append(f"{prev}format=yuv420p[vout]")
open(f"{indir}/filter.txt", "w").write(";".join(chain))
open(f"{indir}/inputs.txt", "w").write(seg_inputs + " " + cap_inputs)
print(f"{n_seg} segments ({total:.1f} s), {len(filters)} captions")
EOF

# shellcheck disable=SC2046
ffmpeg -y -hide_banner -loglevel error -stats \
  $(cat "$IN/inputs.txt") \
  -filter_complex "$(cat "$IN/filter.txt")" -map "[vout]" -map "[aout]" \
  -c:v libx264 -preset medium -crf 20 -r 24 -c:a aac -b:a 160k -movflags +faststart "$OUT"
ls -la "$OUT"
ffprobe -v error -show_entries format=duration:stream=codec_name,width,height -of default=nw=1 "$OUT"
