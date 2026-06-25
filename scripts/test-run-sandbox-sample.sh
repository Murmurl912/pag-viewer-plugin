#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SAMPLE_PATH="$ROOT_DIR/reference/libpag/web/lite/demo/assets/frames.pag"
SAMPLE_ABS="$(cd "$(dirname "$SAMPLE_PATH")" && pwd)/$(basename "$SAMPLE_PATH")"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "Expected output to contain: $needle" >&2
    echo "Actual output:" >&2
    echo "$haystack" >&2
    exit 1
  fi
}

default_output="$(PAG_VIEWER_DRY_RUN=1 "$ROOT_DIR/scripts/run-sandbox-sample.sh")"
assert_contains "$default_output" "./gradlew"
assert_contains "$default_output" "-PpagViewerOpenOnStartup=$SAMPLE_ABS"
assert_contains "$default_output" "runIde"
assert_contains "$default_output" "--args"
assert_contains "$default_output" "$ROOT_DIR"

studio_output="$(PLATFORM_LOCAL_PATH="/Applications/Android Studio.app" PAG_VIEWER_DRY_RUN=1 "$ROOT_DIR/scripts/run-sandbox-sample.sh")"
assert_contains "$studio_output" "-PplatformLocalPath=/Applications/Android Studio.app"
assert_contains "$studio_output" "-PpagViewerOpenOnStartup=$SAMPLE_ABS"
assert_contains "$studio_output" "$ROOT_DIR"
