#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBPAG_SHA="$(git -C "$ROOT_DIR/reference/libpag" rev-parse HEAD)"

hash_files() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$@" | sha256sum
  else
    shasum -a 256 "$@" | shasum -a 256
  fi
}

BUILD_HASH="$(
  hash_files \
    "$ROOT_DIR/scripts/build-libpag-runtime.sh" \
    "$ROOT_DIR/scripts/build-libpag-runtime.ps1" |
    awk '{print substr($1, 1, 16)}'
)"

printf 'libpag-%s-%s\n' "${LIBPAG_SHA:0:12}" "$BUILD_HASH"
