#!/usr/bin/env bash
# The fake adb for scripts/bench-test.sh. It answers exactly the calls scripts/bench.sh makes from
# fixture files and never talks to a device. Cut down from lightphone-dev's lp3 plugin fake adb (MIT,
# same author) to what bench.sh needs. It compares its arguments as strings and never executes or
# evaluates them; any other call exits 97.
#
# FAKE_ADB_STATE is a directory holding:
#   dev/<serial>/model   getprop ro.product.model
#   dev/<serial>/avd     `emu avd name` (emulators only)
#   lines                what `logcat -d -s PongPerf:D PongEvent:D` prints
#   nodone               if present, that logcat leaves out the "done" line
#   log                  every call, one per line
set -uo pipefail
state="${FAKE_ADB_STATE:?FAKE_ADB_STATE not set}"
printf 'adb %s\n' "$*" >>"$state/log"
[ "${1:-}" = -s ] && [ -n "${2:-}" ] || { echo "fake adb: call without -s: $*" >&2; exit 98; }
dev="$state/dev/$2"
[ -d "$dev" ] || { echo "error: device '$2' not found" >&2; exit 1; }
shift 2

if [ "$#" -eq 3 ] && [ "$1" = install ] && [ "$2" = -r ]; then
  [ -f "$3" ] || { echo "fake adb: no APK at $3" >&2; exit 1; }
  echo "Success"
  exit 0
fi
case "$*" in
  "shell getprop ro.product.model") cat "$dev/model" ;;
  "emu avd name") cat "$dev/avd" && echo OK ;;
  "shell input keyevent KEYCODE_WAKEUP" | "shell wm dismiss-keyguard" | "logcat -c") ;;
  "shell am force-stop com.yarosz.pong") ;;
  "shell am start -W -n com.yarosz.pong/com.thelightphone.sdk.LightActivity") echo "Status: ok" ;;
  "logcat -d -s PongPerf:D PongEvent:D")
    if [ -f "$state/nodone" ]; then grep -v 'PongPerf: done' "$state/lines"; else cat "$state/lines"; fi ;;
  *) echo "fake adb: unknown call: $*" >&2; exit 97 ;;
esac
