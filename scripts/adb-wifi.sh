#!/usr/bin/env bash
# Keep a stable wireless adb connection without relying on Android's
# "Wireless debugging" pairing (which HyperOS drops constantly).
#
# Strategy: bootstrap once over USB *or* over a live wireless-debugging
# session, then switch adbd into legacy fixed-port TCP mode (`adb tcpip 5555`).
# That listener has no mDNS dependency, no random port, and no pairing state,
# so reconnecting is just `adb connect <ip>:5555`. It survives Wi-Fi drops,
# screen-off and app restarts; it is lost only on device reboot.
#
# Usage:
#   scripts/adb-wifi.sh            # connect (bootstrap if a device is attached)
#   scripts/adb-wifi.sh watch      # reconnect automatically whenever it drops
#   scripts/adb-wifi.sh status     # show devices + remembered IP
set -uo pipefail

PORT="${ADB_WIFI_PORT:-5555}"
STATE_FILE="${ADB_WIFI_STATE:-$HOME/.cache/adb-wifi-ip}"
CMD="${1:-connect}"

log() { printf '%s\n' "$*" >&2; }

# Serials of devices in `device` state, one per line.
online_serials() {
  adb devices | awk 'NR>1 && $2=="device" {print $1}'
}

# A serial reachable right now that is *not* our tcp:PORT transport, i.e. USB
# or an active wireless-debugging session we can bootstrap from.
bootstrap_serial() {
  online_serials | grep -v ":$PORT\$" | head -1
}

connected_over_tcp() {
  online_serials | grep -q ":$PORT\$"
}

device_ip() {
  local serial="$1" ip
  ip=$(adb -s "$serial" shell ip -o -4 addr show wlan0 2>/dev/null \
       | awk '{print $4}' | cut -d/ -f1 | tr -d '\r' | head -1)
  if [[ -z "$ip" ]]; then
    ip=$(adb -s "$serial" shell ip route get 1.1.1.1 2>/dev/null \
         | sed -n 's/.* src \([0-9.]*\).*/\1/p' | tr -d '\r' | head -1)
  fi
  printf '%s' "$ip"
}

# Drop the half-dead entries adb keeps after the phone vanishes; without this
# `adb connect` cheerfully reports success against a corpse transport.
prune_offline() {
  local serial
  while read -r serial; do
    [[ -n "$serial" ]] && adb disconnect "$serial" >/dev/null 2>&1
  done < <(adb devices | awk 'NR>1 && $2=="offline" {print $1}')
}

try_connect() {
  local ip="$1"
  [[ -z "$ip" ]] && return 1
  adb disconnect "$ip:$PORT" >/dev/null 2>&1
  adb connect "$ip:$PORT" >/dev/null 2>&1
  connected_over_tcp
}

# Turn any reachable transport into a fixed-port TCP listener and connect to it.
bootstrap() {
  local serial ip
  serial=$(bootstrap_serial)
  [[ -z "$serial" ]] && return 1

  ip=$(device_ip "$serial")
  if [[ -z "$ip" ]]; then
    log "Device $serial is up but has no wlan0 IPv4 address — is Wi-Fi on?"
    return 1
  fi

  log "Arming tcp:$PORT on $serial ($ip)"
  adb -s "$serial" tcpip "$PORT" >/dev/null 2>&1 || true
  # adbd restarts its listener; give it a moment before dialling in.
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    try_connect "$ip" && { printf '%s\n' "$ip" >"$STATE_FILE"; return 0; }
    sleep 1
  done
  return 1
}

connect() {
  prune_offline
  connected_over_tcp && { log "Already connected on tcp:$PORT"; return 0; }

  # 1. Remembered IP — the common case, costs one round trip.
  if [[ -f "$STATE_FILE" ]] && try_connect "$(<"$STATE_FILE")"; then
    log "Reconnected to $(<"$STATE_FILE"):$PORT"
    return 0
  fi

  # 2. Bootstrap from USB or a live wireless-debugging session.
  if bootstrap; then
    log "Connected to $(<"$STATE_FILE"):$PORT"
    return 0
  fi

  # 3. Last resort: whatever mDNS still knows about.
  local mdns_ip
  mdns_ip=$(adb mdns services 2>/dev/null \
            | awk '/_adb-tls-connect/ {print $3}' | cut -d: -f1 | head -1)
  if try_connect "$mdns_ip"; then
    printf '%s\n' "$mdns_ip" >"$STATE_FILE"
    log "Connected to $mdns_ip:$PORT via mDNS"
    return 0
  fi

  log "No device. Plug in USB once and re-run, or enable Wireless debugging"
  log "and pair, then re-run to convert it to a stable tcp:$PORT connection."
  return 1
}

case "$CMD" in
  connect)
    connect
    ;;
  watch)
    log "Watching tcp:$PORT — Ctrl-C to stop"
    while true; do
      if ! connected_over_tcp; then
        log "--- link down, reconnecting ---"
        connect || true
      fi
      sleep 5
    done
    ;;
  status)
    adb devices -l
    [[ -f "$STATE_FILE" ]] && log "Remembered IP: $(<"$STATE_FILE")"
    exit 0
    ;;
  *)
    log "Usage: $0 [connect|watch|status]"
    exit 2
    ;;
esac
