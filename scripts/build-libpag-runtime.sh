#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_OS="${1:-}"
TARGET_ARCH="${2:-x86_64}"

case "$TARGET_OS" in
  macos)
    PAG_PLATFORM="mac"
    ;;
  linux)
    PAG_PLATFORM="linux"
    ;;
  *)
    echo "Usage: $0 <macos|linux> [x86_64|aarch64]" >&2
    exit 1
    ;;
esac

case "$TARGET_ARCH" in
  x86_64|amd64)
    PAG_ARCH="x64"
    RESOURCE_ARCH="x86_64"
    ;;
  aarch64|arm64)
    PAG_ARCH="arm64"
    RESOURCE_ARCH="aarch64"
    ;;
  *)
    echo "Unsupported architecture: $TARGET_ARCH" >&2
    exit 1
    ;;
esac

OUT_DIR="$ROOT_DIR/build/native-runtimes/$TARGET_OS-$RESOURCE_ARCH"
BUILD_OUT="$ROOT_DIR/build/libpag-build/$TARGET_OS-$RESOURCE_ARCH"
rm -rf "$OUT_DIR" "$BUILD_OUT"
mkdir -p "$OUT_DIR" "$BUILD_OUT"

(
  cd "$ROOT_DIR/reference/libpag"
  npm install -g depsync --silent
  node build_pag \
    -p "$PAG_PLATFORM" \
    -a "$PAG_ARCH" \
    -DPAG_USE_C=ON \
    -DPAG_BUILD_SHARED=ON \
    -DPAG_BUILD_TESTS=OFF \
    -DPAG_BUILD_FRAMEWORK=OFF \
    -o "$BUILD_OUT"
)

LIBRARY_PATH="$(find "$BUILD_OUT" -type f \( -name 'libpag.dylib' -o -name 'libpag.so' \) -print -quit)"
if [[ -z "$LIBRARY_PATH" ]]; then
  echo "libpag dynamic library was not produced under $BUILD_OUT" >&2
  find "$BUILD_OUT" -maxdepth 5 -type f | sort >&2
  exit 1
fi

cp "$LIBRARY_PATH" "$OUT_DIR/"
echo "Staged native runtime:"
find "$OUT_DIR" -type f -print
