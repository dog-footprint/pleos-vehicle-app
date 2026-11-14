#!/usr/bin/env python3
"""Post-render checks for the demo video (3rd review G1/G2).

    scripts/check-demo.py docs/demo/dog-footprint-demo.mp4 build/demo

1. Every voice line logged during the take (build/demo/tts_events.json: absolute time in the final video)
   must have sound of at least -35 dB within ±2 s. One miss fails the run.
2. No frame may show the Settings tab selected: the GNB button is filled white when selected, so a pixel block
   inside the button but away from its icon (871,516 on 2560x1440 → 653,387 on the 1920-wide render) must
   stay dark (measured: 20 unselected, 255 selected).
Prints a table and exits 1 on any failure.
"""
import json, os, subprocess, sys

VIDEO, IN = sys.argv[1], sys.argv[2]
THRESH_DB, WINDOW = -35.0, 2.0
events = json.load(open(f"{IN}/tts_events.json"))

def max_db(t0, t1):
    out = subprocess.run(
        ["ffmpeg", "-v", "info", "-ss", f"{max(0, t0):.2f}", "-t", f"{t1 - max(0, t0):.2f}", "-i", VIDEO, "-vn", "-af", "volumedetect", "-f", "null", "-"],
        capture_output=True, text=True).stderr
    for line in out.splitlines():
        if "max_volume" in line:
            return float(line.split("max_volume:")[1].split("dB")[0])
    return -99.0

ok = True
print(f"{'t':>7}  {'max dB':>7}  result  text")
for e in events:
    db = max_db(e["t"] - WINDOW, e["t"] + WINDOW)
    passed = db >= THRESH_DB
    ok &= passed
    print(f"{e['t']:7.1f}  {db:7.1f}  {'PASS' if passed else 'FAIL'}    {e['text'][:48]}")
if not events:
    print("no voice events logged — FAIL"); ok = False

# Calm music must be audible while it is on (events.json from record-demo.sh: calm_on / calm_off in video time).
calm = json.load(open(f"{IN}/calm_range.json")) if os.path.exists(f"{IN}/calm_range.json") else None
if calm:
    db = max_db(calm["on"] + 2.0, calm["off"] - 1.0)
    passed = db >= -40.0
    ok &= passed
    print(f"calm music {calm['on']:.1f}–{calm['off']:.1f} s: max {db:.1f} dB  {'PASS' if passed else 'FAIL'}")
else:
    print("calm music range not logged — FAIL"); ok = False

# Background bed: outside the voice lines (±1 s) and the app's own music, every 1 s window must keep ≥ -40 dB.
vw = json.load(open(f"{IN}/voice_windows.json")) if os.path.exists(f"{IN}/voice_windows.json") else []
total = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", VIDEO]))
excluded = [(w["start"] - 1.0, w["end"] + 1.0) for w in vw] + ([(calm["on"] - 1.0, calm["off"] + 1.0)] if calm else [])
def covered(t0, t1): return any(a <= t0 and t1 <= b or (t0 < b and t1 > a) for a, b in excluded)
gaps = []
t = 1.0
while t + 1.0 < total - 1.0:
    if not covered(t, t + 1.0):
        db = max_db(t, t + 1.0)
        if db < -40.0: gaps.append((t, db))
    t += 1.0
if gaps:
    ok = False
    print(f"background bed below -40 dB in {len(gaps)} window(s): " + ", ".join(f"{t:.0f}s {db:.0f}dB" for t, db in gaps[:8]) + (" …" if len(gaps) > 8 else ""))
else:
    print("background bed ≥ -40 dB outside voice/music: PASS")

raw = subprocess.run(
    ["ffmpeg", "-v", "error", "-i", VIDEO, "-vf", "fps=4,crop=6:6:650:384,scale=1:1,format=gray", "-f", "rawvideo", "-"],
    capture_output=True).stdout
bright = [i / 4 for i, b in enumerate(raw) if b > 128]
print(f"settings-tab frames (4 fps): {len(bright)}" + (f" at {bright[:6]} s" if bright else ""))
ok &= not bright
dur = float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", VIDEO]))
print(f"total length: {int(dur // 60)}:{int(dur % 60):02d}")
print("RESULT:", "PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)
