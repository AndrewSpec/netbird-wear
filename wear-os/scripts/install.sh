#!/usr/bin/env bash
# Explicit serials prevent installing the phone APK on a watch by device order.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ $# -ne 2 ]]; then
  echo "usage: $0 <phone-serial> <watch-serial>" >&2
  echo "Read serials with: adb devices -l" >&2
  exit 1
fi
PHONE="$1"
WATCH="$2"
[[ "$PHONE" != "$WATCH" ]] || { echo "Phone and watch serials must differ" >&2; exit 1; }
PHONE_TYPE="$(adb -s "$PHONE" shell getprop ro.build.characteristics)"
WATCH_TYPE="$(adb -s "$WATCH" shell getprop ro.build.characteristics)"
[[ "$PHONE_TYPE" != *watch* ]] || { echo "Phone serial points to a watch" >&2; exit 1; }
[[ "$WATCH_TYPE" == *watch* ]] || { echo "Watch serial does not point to a watch" >&2; exit 1; }
for apk in netbird-wear-release.apk netbird-watch-release.apk; do
  [[ -f "$ROOT/dist/$apk" ]] || { echo "Missing dist/$apk" >&2; exit 1; }
done
adb -s "$PHONE" install -r "$ROOT/dist/netbird-wear-release.apk"
adb -s "$WATCH" install -r "$ROOT/dist/netbird-watch-release.apk"
echo "Open NetBird on the phone, sign in, approve VPN and allow background activity."
echo "Then open NetBird on the watch and add its tile. No apps were uninstalled."
