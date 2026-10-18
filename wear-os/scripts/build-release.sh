#!/usr/bin/env bash
# Build signed release APKs (sideloading build signed with the local debug
# keystore from .android-home; suitable for personal use, not for Play).
set -euo pipefail
source "$(dirname "$0")/env.sh"

"$ROOT/scripts/build-netbird-aar.sh"
"$ROOT/scripts/apply-netbird-patch.sh" "$CLONE_DIR"

echo "==> Building release phone APK"
cd "$CLONE_DIR"
NETBIRD_UPLOAD_STORE_FILE="$ANDROID_USER_HOME/debug.keystore" \
NETBIRD_UPLOAD_STORE_PASSWORD="${NETBIRD_UPLOAD_STORE_PASSWORD:-android}" \
NETBIRD_UPLOAD_KEY_ALIAS="${NETBIRD_UPLOAD_KEY_ALIAS:-androiddebugkey}" \
NETBIRD_UPLOAD_KEY_PASSWORD="${NETBIRD_UPLOAD_KEY_PASSWORD:-android}" \
  ./gradlew :app:assembleRelease --no-daemon \
    -PversionName="${VERSION_NAME:-1.0.3-wear}" \
    -PversionCode="${VERSION_CODE:-4}"

echo "==> Building release watch APK"
cd "$ROOT"
./gradlew :wear:assembleRelease --no-daemon

mkdir -p "$ROOT/dist"
cp "$CLONE_DIR/app/build/outputs/apk/release/app-release.apk" "$ROOT/dist/netbird-wear-release.apk"
cp "$ROOT/wear/build/outputs/apk/release/wear-release.apk" "$ROOT/dist/netbird-watch-release.apk"
echo "==> dist/netbird-wear-release.apk"
echo "==> dist/netbird-watch-release.apk"
