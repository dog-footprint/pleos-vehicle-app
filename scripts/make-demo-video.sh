#!/usr/bin/env bash
# Renders the recorded demo into an MP4 with a Korean caption strip, optionally re-creating the voice lines,
# then runs the post-render checks (voice audible at every logged line, Settings tab never visible).
#
#   scripts/make-demo-video.sh [build/demo] [docs/demo/dog-footprint-demo.mp4] [--voice synth|none]
#
# --voice synth: the emulator capture only carries the media bus, so the Pleos voice is missing from the take.
# Each line logged by record-demo.sh is rendered with macOS `say -v Yuna` and mixed in at the logged time, and
# the captions of those scenes carry "(음성 재현)". Needs ffmpeg (libx264) and Pillow; captions use Apple SD Gothic Neo.
set -euo pipefail
cd "$(dirname "$0")/.."
IN=build/demo; OUT=docs/demo/dog-footprint-demo.mp4; VOICE=none
POS=()
while [[ $# -gt 0 ]]; do case $1 in --voice) VOICE=$2; shift 2 ;; *) POS+=("$1"); shift ;; esac; done
[[ ${#POS[@]} -ge 1 ]] && IN=${POS[0]}; [[ ${#POS[@]} -ge 2 ]] && OUT=${POS[1]}
mkdir -p "$(dirname "$OUT")" "$IN/captions" "$IN/voice"

python3 - "$IN" "$VOICE" <<'EOF'
import json, subprocess, sys, os, glob, re
from PIL import Image, ImageDraw, ImageFont

indir, voice = sys.argv[1], sys.argv[2]
data = json.load(open(f"{indir}/scenes.json"))
# Segments are played in scene order (re-takes get new numbers), so order them by the first scene they hold.
IDS = ["intro","profile","board","harness","depart","heat","window","break","arrive","preexit","alone","return","footprints","outro"]
present = sorted(int(re.search(r"demo-(\d+)\.webm", f).group(1)) for f in glob.glob(f"{indir}/demo-*.webm"))
first_scene = {k: min((IDS.index(s["id"]) for s in data["scenes"] if s["segment"] == k and s["id"] in IDS), default=99) for k in present}
order = sorted(present, key=lambda k: (first_scene[k], k))
seg_dur = {k: float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", f"{indir}/demo-{k}.webm"])) for k in order}
offsets = {}; acc = 0.0
for k in order: offsets[k] = acc; acc += seg_dur[k]
total = acc
n_seg = len(order)
scenes = sorted(({**s, "start": round(offsets[s["segment"]] + min(s["start"], seg_dur[s["segment"]]), 2)} for s in data["scenes"] if s["segment"] in offsets), key=lambda s: s["start"])

# Voice lines → absolute times on the concatenated timeline (for the mix and for the checks).
raw = json.load(open(f"{indir}/tts_segment_events.json")) if os.path.exists(f"{indir}/tts_segment_events.json") else []
events = sorted(({"t": round(offsets[e["segment"]] + e["t_in_segment"], 2), "text": e["text"]} for e in raw if e["segment"] in offsets), key=lambda e: e["t"])
json.dump(events, open(f"{indir}/tts_events.json", "w"), ensure_ascii=False)
# Marks from the take (calm music on/off) on the same timeline, for the checks.
marks = json.load(open(f"{indir}/events.json")) if os.path.exists(f"{indir}/events.json") else []
abs_marks = {m["name"]: round(offsets[m["segment"]] + m["t_in_segment"], 2) for m in marks if m["segment"] in offsets}
if "calm_on" in abs_marks and "calm_off" in abs_marks:
    json.dump({"on": abs_marks["calm_on"], "off": abs_marks["calm_off"]}, open(f"{indir}/calm_range.json", "w"))
bed_from = 0.0   # low CC0 bed from the first frame; sidechain-ducked under the voice lines, silenced while the app's own music plays

W, H = 1920, 120
font = ImageFont.truetype("/System/Library/Fonts/AppleSDGothicNeo.ttc", 36)
MAX_W = W - 2 * 72
def wrap_px(text):
    lines, cur = [], ""
    for word in text.split(" "):
        cand = (cur + " " + word).strip()
        if font.getlength(cand) <= MAX_W or not cur: cur = cand
        else: lines.append(cur); cur = word
    if cur: lines.append(cur)
    return lines
filters = []
for i, s in enumerate(scenes):
    end = scenes[i + 1]["start"] if i + 1 < len(scenes) else total
    caption = s["caption"]
    img = Image.new("RGBA", (W, H), (19, 20, 23, 255)); d = ImageDraw.Draw(img)
    lines = wrap_px(caption)[:2]; y = (H - 48 * len(lines)) // 2
    for line in lines: d.text((72, y), line, font=font, fill=(255, 255, 255, 255)); y += 48
    path = f"{indir}/captions/{i:02d}.png"; img.save(path); filters.append((path, s["start"], end))
if voice == "synth":
    note = Image.new("RGBA", (W, 48), (19, 20, 23, 255)); nd = ImageDraw.Draw(note)
    nd.text((72, 6), "음성 안내는 Pleos TTS 문장을 재현했습니다", font=ImageFont.truetype("/System/Library/Fonts/AppleSDGothicNeo.ttc", 28), fill=(163, 163, 163, 255))
    note_path = f"{indir}/captions/note.png"; note.save(note_path)
else:
    note_path = None

# Voice track: one `say` render per line, delayed to its time, mixed over the capture.
voice_inputs, voice_chain = [], []
windows = []
if voice == "synth":
    for n, e in enumerate(events):
        aiff = f"{indir}/voice/{n:02d}.aiff"
        subprocess.run(["say", "-v", "Yuna", "-r", "190", "-o", aiff, e["text"]], check=True)
        dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", aiff]))
        windows.append({"start": e["t"], "end": round(e["t"] + dur, 2)})
        voice_inputs.append(aiff); voice_chain.append((n, int(e["t"] * 1000)))
json.dump(windows, open(f"{indir}/voice_windows.json", "w"))

seg_inputs = " ".join(f"-i {indir}/demo-{k}.webm" for k in order)
cap_inputs = " ".join(f"-i {p}" for p, _, _ in filters)
voi_inputs = " ".join(f"-i {p}" for p in voice_inputs)
extra_inputs = ""
bed = "app/src/main/res/raw/calm_aria.m4a"   # CC0 (Open Goldberg Variations), the same file the app ships
if bed_from is not None and os.path.exists(bed): extra_inputs += f" -i {bed}"
if note_path: extra_inputs += f" -i {note_path}"
# The emulator writes audio only while something plays (timestamps jump over silence), so rebuild each
# segment's audio as a continuous track first; otherwise the concat and the voice delays drift.
chain = [f"[{k}:a]aresample=async=1:first_pts=0[a{k}]" for k in range(n_seg)]
chain.append("".join(f"[{k}:v][a{k}]" for k in range(n_seg)) + f"concat=n={n_seg}:v=1:a=1[cv][ca]")
base_idx = n_seg + len(filters)
delayed = []
for n, ms in voice_chain:
    chain.append(f"[{base_idx + n}:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=1.0,adelay={ms}|{ms}[vo{n}]"); delayed.append(f"[vo{n}]")
next_idx = base_idx + len(voice_chain)
voice_mix = None
if delayed:
    chain.append(f"{''.join(delayed)}amix=inputs={len(delayed)}:duration=longest:normalize=0,apad[voice]")
    voice_mix = "[voice]"
if bed_from is not None and os.path.exists(bed):
    # Loop the bed for the whole length, drop it while the app's calm music plays (same piece), then duck it
    # under the synthesized voice with a sidechain compressor.
    calm_gate = f",volume=enable='between(t,{abs_marks['calm_on']},{abs_marks['calm_off']})':volume=0" if "calm_on" in abs_marks and "calm_off" in abs_marks else ""
    # The Aria's quiet bars drop 30 dB below its peaks; level the bed first (dynaudnorm, 250 ms frames, up to +32 dB on the quiet
    # frames) so it never falls below -40 dB outside the voice lines, then scale to -20 dB.
    chain.append(f"[{next_idx}:a]aloop=loop=-1:size=2e9,atrim=0:{total:.2f},aformat=sample_rates=48000:channel_layouts=stereo,dynaudnorm=f=250:g=7:m=40:p=0.95,volume=0.1{calm_gate}[bedraw]")
    next_idx += 1
    # Fixed gains instead of single-pass loudnorm (which adapts over time and pumped the bed up to full level):
    # capture +18 dB (the emulator records the app's music around -36 dB), voice as rendered, bed -20 dB.
    chain.append("[ca]volume=2[cag]")
    if voice_mix:
        chain.append(f"{voice_mix}asplit=2[vkey][vmix]")
        chain.append("[bedraw][vkey]sidechaincompress=threshold=0.02:ratio=12:attack=20:release=600[bed]")
        chain.append("[cag][vmix][bed]amix=inputs=3:duration=first:normalize=0[mix]")
    else:
        chain.append("[cag][bedraw]amix=inputs=2:duration=first:normalize=0[mix]")
    chain.append("[mix]alimiter=limit=0.9:level=false[aout]")
elif voice_mix:
    chain.append(f"[ca]volume=2[cag];[cag]{voice_mix}amix=inputs=2:duration=first:normalize=0[mix]")
    chain.append("[mix]alimiter=limit=0.9:level=false[aout]")
else:
    chain.append("[ca]volume=2,alimiter=limit=0.9:level=false[aout]")
note_idx = next_idx if note_path else None
chain.append("[cv]scale=1920:1080:flags=lanczos,pad=1920:1200:0:0:color=#131417[base]")
prev = "[base]"
for n, (_, a, b) in enumerate(filters):
    out = f"[v{n}]"; chain.append(f"{prev}[{n_seg + n}:v]overlay=0:1080:enable='between(t,{a},{b})'{out}"); prev = out
if note_idx is not None:
    chain.append(f"{prev}[{note_idx}:v]overlay=0:1152:enable='between(t,0,3)'[vn]"); prev = "[vn]"
chain.append(f"{prev}format=yuv420p[vout]")
open(f"{indir}/filter.txt", "w").write(";".join(chain))
open(f"{indir}/inputs.txt", "w").write(seg_inputs + " " + cap_inputs + " " + voi_inputs + extra_inputs)
print(f"{n_seg} segments ({total:.1f} s), {len(filters)} captions, {len(events)} voice lines ({voice})")
EOF

# shellcheck disable=SC2046
ffmpeg -y -hide_banner -loglevel error -stats \
  $(cat "$IN/inputs.txt") \
  -filter_complex "$(cat "$IN/filter.txt")" -map "[vout]" -map "[aout]" \
  -c:v libx264 -preset medium -crf 20 -r 24 -c:a aac -b:a 160k -movflags +faststart "$OUT"
ls -la "$OUT"
ffprobe -v error -show_entries format=duration:stream=codec_name,width,height -of default=nw=1 "$OUT"
python3 scripts/check-demo.py "$OUT" "$IN"
