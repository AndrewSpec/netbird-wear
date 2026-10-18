#!/usr/bin/env bash
# Build everything: gomobile AAR, patched NetBird phone APK (debug + release),
# Wear OS watch APK (debug + release). First run downloads NDK and dependencies.
set -euo pipefail
source "$(dirname "$0")/env.sh"

"$ROOT/scripts/build-phone-apk.sh"
"$ROOT/scripts/build-watch-apk.sh"
"$ROOT/scripts/build-release.sh"

echo
echo "==> Done. Artifacts:"
ls -lh "$ROOT/dist"/*.apk
