#!/usr/bin/env bash
# Build the NetBird gomobile AAR (Go engine for Android) used by the patched
# client. Cached in .build/netbird.aar; use FORCE=1 to rebuild.
set -euo pipefail
source "$(dirname "$0")/env.sh"

if [[ -f "$AAR_PATH" && "${FORCE:-0}" != "1" ]]; then
  echo "==> Using cached AAR: $AAR_PATH"
  exit 0
fi

# --- NDK ---------------------------------------------------------------------
if [[ ! -d "$ANDROID_NDK_HOME" ]]; then
  echo "==> Downloading Android NDK $NDK_VERSION"
  NDK_ZIP="$ROOT/.toolchain/ndk-r23b.zip"
  if [[ ! -f "$NDK_ZIP" ]]; then
    curl -L --fail -o "$NDK_ZIP" "$NDK_URL"
  fi
  mkdir -p "$ROOT/.toolchain/ndk-extract"
  unzip -q -o "$NDK_ZIP" -d "$ROOT/.toolchain/ndk-extract"
  EXPECTED_NDK="$ROOT/.toolchain/ndk-23.1.7779620"
  [[ "$(realpath -m "$ANDROID_NDK_HOME")" == "$EXPECTED_NDK" ]] || {
    echo "For a custom NDK path, install it there yourself before building" >&2; exit 1;
  }
  [[ "$(realpath "$ROOT/.toolchain/ndk-extract")" == "$ROOT/.toolchain/ndk-extract" ]] || exit 1
  [[ "$(realpath "$ROOT/.toolchain/ndk-extract/android-ndk-r23b")" == "$ROOT/.toolchain/ndk-extract/android-ndk-r23b" ]] || exit 1
  mv "$ROOT/.toolchain/ndk-extract/android-ndk-r23b" "$EXPECTED_NDK"
  rmdir "$ROOT/.toolchain/ndk-extract"
fi
echo "==> NDK: $ANDROID_NDK_HOME"

# --- Upstream source at the pinned commit ------------------------------------
if [[ ! -d "$CLONE_DIR/.git" ]]; then
  echo "==> Cloning $UPSTREAM_REPO"
  mkdir -p "$CLONE_DIR"
  git -C "$CLONE_DIR" init -q
  git -C "$CLONE_DIR" remote add origin "$UPSTREAM_REPO" 2>/dev/null || true
fi
echo "==> Checking out upstream commit $UPSTREAM_COMMIT"
git -C "$CLONE_DIR" fetch -q --depth 1 origin "$UPSTREAM_COMMIT"
git -C "$CLONE_DIR" checkout -q FETCH_HEAD
git -C "$CLONE_DIR" submodule update -q --init --depth 1 netbird

# --- gomobile pinned by netbird/go.mod --------------------------------------
cd "$CLONE_DIR/netbird"
WANT="$(go list -m -f '{{.Version}}' golang.org/x/mobile)"
for tool in gomobile gobind; do
  HAVE=""
  if command -v "$tool" >/dev/null 2>&1; then
    HAVE="$(go version -m "$(command -v "$tool")" 2>/dev/null \
      | awk '$1 == "mod" && $2 == "golang.org/x/mobile" {print $3}')" || true
  fi
  if [[ "$HAVE" != "$WANT" ]]; then
    echo "==> Installing $tool@$WANT"
    go install "golang.org/x/mobile/cmd/$tool@$WANT"
  fi
done

# --- Bind --------------------------------------------------------------------
echo "==> gomobile bind (this compiles the whole NetBird client, be patient)"
CGO_ENABLED=0 gomobile bind \
  -androidapi 26 \
  -target=android/arm64 \
  -o "$AAR_PATH" \
  -javapkg=io.netbird.gomobile \
  -ldflags="-linkmode=external -extldflags=-Wl,-z,max-page-size=16384 -checklinkname=0 \
    -X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/io.netbird.client/cache/wireguard \
    -X github.com/netbirdio/netbird/version.version=wear-1.0.0" \
  "$(pwd)/client/android"

echo "==> AAR ready: $AAR_PATH"
