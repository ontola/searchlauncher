#!/usr/bin/env bash
set -euo pipefail

PKG="${PKG:-com.searchlauncher.app}"
ACTIVITY="${ACTIVITY:-$PKG/.ui.MainActivity}"
DURATION="${1:-10s}"
QUERY="${2:-}"
OUT_DIR="${OUT_DIR:-analysis/traces}"
STAMP="$(date +%Y%m%d-%H%M%S)"
HOST_TRACE="$OUT_DIR/searchlauncher-$STAMP.perfetto-trace"
HOST_GFX="$OUT_DIR/searchlauncher-$STAMP.gfxinfo.txt"
HOST_LOG="$OUT_DIR/searchlauncher-$STAMP.logcat.txt"

duration_seconds() {
  case "$1" in
    *ms) echo 1 ;;
    *s) echo "${1%s}" ;;
    *m) echo "$(( ${1%m} * 60 ))" ;;
    *) echo "$1" ;;
  esac
}

adb_text() {
  local text="$1"
  text="${text// /%s}"
  adb shell input text "$text"
}

mkdir -p "$OUT_DIR"

echo "Starting $ACTIVITY"
adb shell am start -n "$ACTIVITY" >/dev/null
sleep 1

echo "Resetting gfxinfo and logcat"
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null || true
adb logcat -c || true

echo "Recording Perfetto trace for $DURATION"
adb exec-out sh -c \
  "perfetto -o - -t '$DURATION' -b 64mb -a '$PKG' gfx view wm am input sched freq sched/sched_switch sched/sched_wakeup freq/cpu_frequency 2>/dev/null" \
  >"$HOST_TRACE" &
PERFETTO_PID=$!

if [[ -n "$QUERY" ]]; then
  sleep 1
  echo "Typing query: $QUERY"
  adb_text "$QUERY"
else
  echo "Type the search query on the device now."
fi

wait "$PERFETTO_PID"

echo "Collecting gfxinfo and logcat"
adb shell dumpsys gfxinfo "$PKG" >"$HOST_GFX" || true
adb logcat -d -v time -t 3000 \
  SearchRepository:D AndroidRuntime:E ActivityManager:I Choreographer:I '*:S' >"$HOST_LOG" || true

echo "Trace:   $HOST_TRACE"
echo "Gfxinfo: $HOST_GFX"
echo "Logcat:  $HOST_LOG"
echo
echo "Open the trace at https://ui.perfetto.dev and search for SL:"
