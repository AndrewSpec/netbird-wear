#!/usr/bin/env bash
# Shared, reproducible build environment. Source this from the other scripts.
# All caches live inside the repo so the build never writes outside the
# workspace (and can be wiped with `rm -rf .toolchain .gradle-home .build`).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ROOT

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$(command -v javac || true)"
  if [[ -z "$JAVA_BIN" ]]; then
    echo "JDK 17 is required; set JAVA_HOME" >&2
    exit 1
  fi
  export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
fi

export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT/.gradle-home}"
# AGP keeps the debug keystore under the Android user home; point it into the
# workspace so builds do not depend on a writable $HOME.
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$ROOT/.android-home}"
export GOPATH="${GOPATH:-$ROOT/.toolchain/gopath}"
export GOMODCACHE="${GOMODCACHE:-$ROOT/.toolchain/gopath/pkg/mod}"
export GOCACHE="${GOCACHE:-$ROOT/.toolchain/gocache}"
export GOBIN="${GOBIN:-$ROOT/.toolchain/bin}"
export PATH="$GOBIN:$PATH"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ROOT/.toolchain/ndk-23.1.7779620}"

export UPSTREAM_REPO="${UPSTREAM_REPO:-https://github.com/netbirdio/android-client.git}"
export UPSTREAM_COMMIT="${UPSTREAM_COMMIT:-ad7865a2c3cc1bc940bf63512ccab1819ebf53a6}"
export NDK_VERSION="${NDK_VERSION:-23.1.7779620}"
export NDK_URL="${NDK_URL:-https://dl.google.com/android/repository/android-ndk-r23b-linux.zip}"

export BUILD_DIR="${BUILD_DIR:-$ROOT/.build}"
export CLONE_DIR="${CLONE_DIR:-$BUILD_DIR/android-client}"
export AAR_PATH="${AAR_PATH:-$BUILD_DIR/netbird.aar}"

mkdir -p "$BUILD_DIR" "$ROOT/dist" "$ROOT/.toolchain" "$ANDROID_USER_HOME"

if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "Android SDK not found at ANDROID_HOME=$ANDROID_HOME" >&2
  exit 1
fi
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JDK 17 not found at JAVA_HOME=$JAVA_HOME (override with JAVA_HOME=...)" >&2
  exit 1
fi

PATH="$JAVA_HOME/bin:$PATH"
export PATH

# Debug signing key shared by the phone and watch builds (sideloading only).
if [[ ! -f "$ANDROID_USER_HOME/debug.keystore" ]]; then
  echo "==> Creating debug keystore in $ANDROID_USER_HOME"
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$ANDROID_USER_HOME/debug.keystore" \
    -storepass android -alias androiddebugkey -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
fi
