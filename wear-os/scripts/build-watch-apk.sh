#!/usr/bin/env bash
# Build the Wear OS watch APK.
set -euo pipefail
source "$(dirname "$0")/env.sh"

cd "$ROOT"
./gradlew :wear:assembleDebug --no-daemon

mkdir -p "$ROOT/dist"
cp "$ROOT/wear/build/outputs/apk/debug/wear-debug.apk" "$ROOT/dist/netbird-watch-debug.apk"
echo "==> dist/netbird-watch-debug.apk"
