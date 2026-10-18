#!/usr/bin/env bash
# Apply the NetBird Wear patch to a clone of netbirdio/android-client.
set -euo pipefail
source "$(dirname "$0")/env.sh"

DEST="${1:-}"
if [[ -z "$DEST" ]]; then
  echo "usage: $0 <path to android-client clone>" >&2
  exit 1
fi
if [[ ! -d "$DEST/.git" ]]; then
  echo "$DEST is not a git clone" >&2
  exit 1
fi

PATCH="$ROOT/patches/netbird-wear.patch"
if git -C "$DEST" apply --check --whitespace=nowarn "$PATCH" 2>/dev/null; then
  git -C "$DEST" apply --whitespace=nowarn "$PATCH"
  echo "==> Patch applied to $DEST"
elif git -C "$DEST" apply --reverse --check --whitespace=nowarn "$PATCH" 2>/dev/null; then
  echo "==> Patch already applied to $DEST"
else
  echo "Patch does not apply cleanly. No files have been reset or removed." >&2
  echo "Use a fresh clone at $UPSTREAM_COMMIT (or set CLONE_DIR to a new build directory)." >&2
  exit 1
fi

if [[ -f "$AAR_PATH" ]]; then
  cp "$AAR_PATH" "$DEST/gomobile/netbird.aar"
  echo "==> Copied AAR to $DEST/gomobile/netbird.aar"
else
  echo "note: $AAR_PATH missing; run scripts/build-netbird-aar.sh first" >&2
fi
