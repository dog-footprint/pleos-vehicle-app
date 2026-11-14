#!/usr/bin/env python3
"""Writes the demo seed (AppData JSON) with realistic history: 코코 (husky) and seven trips over the last
two weeks with varied distance, breaks, events, comfort scores and moods, dated relative to now so the
Footprints list and the weekly total stay meaningful whenever the demo is recorded.

    scripts/demo-seed.py [out.json] [--control] [--utc-offset=9]   # --control turns the experimental Vehicle Control on
"""
import json, sys, time, uuid

CONTROL = "--control" in sys.argv
# Hour offset of the clock the emulator shows (record-demo.sh sets a zone so the status bar reads ~14:00);
# trip start hours below are local hours in that zone — daytime and evening only.
UTC_OFFSET = next((float(a.split("=", 1)[1]) for a in sys.argv[1:] if a.startswith("--utc-offset=")), 9.0)
OUT = next((a for a in sys.argv[1:] if not a.startswith("--")), "scripts/demo-seed.json")
NOW = int(time.time() * 1000)
DAY = 24 * 3600 * 1000
PENALTY = {"BRAKE_BURST": 10, "SPORT_MODE": 15, "WINDOW_OPEN": 10, "BELT_UNBUCKLED": 10, "TEMP_OUT_OF_BAND": 5}

dog = {
    "id": "coco", "name": "코코", "breedKey": "husky", "size": "LARGE", "coat": "DOUBLE", "brachycephalic": False,
    "ageGroup": "ADULT", "birthYear": 2022, "birthMonth": 5, "weightKg": 22.0, "bodyCondition": "IDEAL",
    "respiratoryOrHeartIssue": False, "motionSickness": "MILD", "restraint": "HARNESS", "seat": "ROW_2_LEFT",
    "createdAt": NOW - 40 * DAY,
}

# (days ago, start hour, km, minutes, breaks, events after TRIP_START, mood, destination, note)
PAST = [
    (13, 9,  42.0, 55, 1, ["BREAK_TAKEN"], "HAPPY", "양평", None),
    (11, 18, 6.5, 14, 0, [], "OKAY", None, None),
    (9,  8,  118.0, 140, 2, ["BELT_UNBUCKLED", "BREAK_TAKEN", "WINDOW_OPEN", "BREAK_TAKEN"], "STRESSED", "강릉", "휴게소에서 물을 잘 마셨어요"),
    (7,  16, 12.3, 22, 0, ["BRAKE_BURST"], "OKAY", None, None),
    (5,  10, 27.8, 38, 0, ["TEMP_OUT_OF_BAND", "CLIMATE_APPLIED"], "HAPPY", "애견 운동장", None),
    (3,  19, 8.9, 17, 0, [], "HAPPY", None, None),
    (1,  11, 63.4, 78, 1, ["BREAK_TAKEN", "CALM_ON", "CALM_OFF"], "HAPPY", "속초", "진정 음악에 잘 자더라고요"),
]

trips = []
odo = 12000.0
for days_ago, hour, km, minutes, breaks, evs, mood, dest, note in PAST:
    start = NOW - days_ago * DAY
    start -= (start % DAY)                          # midnight UTC of that day …
    start += int((hour - UTC_OFFSET) * 3600 * 1000)  # … shifted to the local hour in the recording zone
    end = start + minutes * 60 * 1000
    events = [{"at": start, "type": "TRIP_START", "detail": None}]
    step = (end - start) // (len(evs) + 2)
    for i, t in enumerate(evs, 1):
        detail = {"TEMP_OUT_OF_BAND": "25.0°C", "WINDOW_OPEN": "60%"}.get(t)
        events.append({"at": start + step * i, "type": t, "detail": detail})
    events.append({"at": end, "type": "TRIP_END", "detail": None})
    counts = {}
    for e in events:
        counts[e["type"]] = counts.get(e["type"], 0) + 1
    score = max(0, 100 - sum(PENALTY.get(t, 0) * n for t, n in counts.items()))
    trips.append({
        "id": str(uuid.uuid5(uuid.NAMESPACE_URL, f"demo-trip-{days_ago}")), "dogId": "coco",
        "startedAt": start, "endedAt": end, "startKm": round(odo, 1), "endKm": round(odo + km, 1),
        "drivingSec": minutes * 60, "breaks": breaks, "startSoc": 82, "endSoc": max(10, 82 - int(km / 4)),
        "startLat": None, "startLon": None, "endLat": None, "endLon": None,
        "destinationName": dest, "comfortScore": score, "events": events, "mood": mood, "note": note,
    })
    odo += km

data = {
    "dogs": [dog], "activeDogId": "coco", "aboardDogId": None, "lastMealAt": None, "trips": trips,
    "settings": {"controlEnabled": CONTROL, "pleosTts": True, "calmVolume": 0.6, "calmMode": "MUSIC"},
}
json.dump(data, open(OUT, "w"), ensure_ascii=False)
print(f"{OUT}: {len(trips)} trips, {sum(t['endKm'] - t['startKm'] for t in trips):.1f} km, control={'on' if CONTROL else 'off'}")
