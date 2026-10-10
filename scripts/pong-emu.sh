#!/usr/bin/env bash
# adb, aimed at the Pong emulator only. The emulator is found by AVD name (PONG_AVD, default
# LightPhone3-pong), never by position in `adb devices`: other Tools run their own emulators on this
# machine, and a script that took the first one would drive the wrong Tool. Fails when no emulator, or
# more than one, runs that AVD.
#
#   scripts/pong-emu.sh serial          print the serial (e.g. emulator-5560)
#   scripts/pong-emu.sh <adb args...>   run adb -s <serial> <adb args...>
set -uo pipefail
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
avd=${PONG_AVD:-LightPhone3-pong}

serial=$("$adb" devices | awk '/^emulator-[0-9]+\tdevice/{print $1}' | while read -r s; do
  [ "$("$adb" -s "$s" emu avd name 2>/dev/null </dev/null | head -1 | tr -d '\r')" = "$avd" ] && echo "$s"; done)
if [ -z "$serial" ]; then
  echo "pong-emu: no emulator runs AVD $avd (mise run emu, or scripts/emulator/RECIPE.md)" >&2
  exit 1
fi
if [ "$(wc -l <<<"$serial")" -ne 1 ]; then
  echo "pong-emu: several emulators run AVD $avd ($(echo $serial)); stop the extra one" >&2
  exit 1
fi

if [ "${1:-}" = serial ]; then echo "$serial"; exit 0; fi
exec "$adb" -s "$serial" "$@"
