#!/usr/bin/env bash
# Drives the Pleos Connect Virtual Console (the emulator's vehicle-signal simulator) from the terminal.
# The console only accepts writes while a "browser session" heartbeat is alive, so every write
# sends one first. Run `scripts/vehicle-sim.sh forward` once per emulator boot.
#
# Usage:
#   scripts/vehicle-sim.sh forward                  # adb forward tcp:8088
#   scripts/vehicle-sim.sh gear Drive|Park|Reverse|Neutral
#   scripts/vehicle-sim.sh odometer 123.4           # km
#   scripts/vehicle-sim.sh speed 40                 # km/h   (controls endpoint)
#   scripts/vehicle-sim.sh brake 100                # %      (pedal pressed when > 0)
#   scripts/vehicle-sim.sh hvac "driver_temp=27&fan=5&power=1"   # hvacSnapshot field names
#   scripts/vehicle-sim.sh window rl 60             # fl|fr|rl|rr, 0..100 %
#   scripts/vehicle-sim.sh door rl 1                # fl|fr|rl|rr, 1=open 0=closed
#   scripts/vehicle-sim.sh trunk 1
#   scripts/vehicle-sim.sh seat rear_left true      # driver|passenger|rear_left|rear_right occupancy
#   scripts/vehicle-sim.sh state                    # condensed state dump
set -euo pipefail
B=${CONSOLE:-http://127.0.0.1:8088}

hb() { curl -s -m 3 -X POST "$B/browser/session?client=dogfootprint-cli" -o /dev/null || true; }
put() { hb; curl -s -m 5 -X PUT "$B$1" -w " [%{http_code}]\n"; }

case "${1:-}" in
  forward)  adb forward tcp:8088 tcp:8088 ;;
  gear)     put "/vehicle/gear?gear=$2" ;;
  odometer) put "/vehicle/odometer?traveled_distance_km=$2" ;;
  speed)    put "/vehicle/controls?speed=$2" ;;
  brake)    put "/vehicle/controls?brake=$2" ;;
  hvac)     put "/vehicle/hvac?$2" ;;
  window)   put "/vehicle/body?window_$2=$3" ;;
  door)     put "/vehicle/body?door_$2=$3" ;;
  trunk)    put "/vehicle/body?trunk=$2" ;;
  seat)     put "/vehicle/seat?kind=occupied&area=$2&value=$3" ;;
  state)
    curl -s -m 5 "$B/vehicle/state" | python3 -c '
import json, sys
s = json.load(sys.stdin)
h, b, c, o = s["hvac"], s["body"], s["controls"], s["odometer"]
print("gear", s["gear"].get("actual"), "| speed", c["speed_kph"], "brake", c["brake_pct"], "| odo", o["traveled_distance_km"], "km")
print("hvac power", h["power_on"], "fan", h["fan_speed"], "driver", h["driver"]["temp_c"], "passenger", h["passenger"]["temp_c"], "dir", h["driver"]["fan_direction_label"])
print("windows", b["windows"], "| doors", b["doors"], "| trunk", b["closures"]["trunk"])
print("seat occupied", s["seat"]["occupied"])
' ;;
  *) sed -n '2,17p' "$0"; exit 1 ;;
esac
