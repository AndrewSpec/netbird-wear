#!/usr/bin/env bash
# Build the patched NetBird phone APK (NetBird Wear Edition).
set -euo pipefail
source "$(dirname "$0")/env.sh"

"$ROOT/scripts/build-netbird-aar.sh"
"$ROOT/scripts/apply-netbird-patch.sh" "$CLONE_DIR"

echo "==> Building phone APK"
cd "$CLONE_DIR"
./gradlew :app:assembleDebug --no-daemon \
  -PversionName="${VERSION_NAME:-1.0.3-wear}" \
  -PversionCode="${VERSION_CODE:-4}"

mkdir -p "$ROOT/dist"
cp "$CLONE_DIR/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/dist/netbird-wear-debug.apk"
echo "==> dist/netbird-wear-debug.apk"
