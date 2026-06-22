#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SAMPLE_PATH="${1:-"$ROOT_DIR/reference/libpag/web/lite/demo/assets/frames.pag"}"

if [[ ! -f "$SAMPLE_PATH" ]]; then
  echo "PAG file not found: $SAMPLE_PATH" >&2
  echo "Usage: scripts/run-sandbox-sample.sh [path/to/file.pag]" >&2
  exit 1
fi

cd "$ROOT_DIR"
SAMPLE_ABS="$(cd "$(dirname "$SAMPLE_PATH")" && pwd)/$(basename "$SAMPLE_PATH")"

if [[ "${PAG_VIEWER_DRY_RUN:-}" == "1" ]]; then
  if [[ -n "${PLATFORM_LOCAL_PATH:-}" ]]; then
    printf './gradlew\n-PplatformLocalPath=%s\n-PpagViewerOpenOnStartup=%s\nrunIde\n--args\n%s\n' "$PLATFORM_LOCAL_PATH" "$SAMPLE_ABS" "$ROOT_DIR"
  else
    printf './gradlew\n-PpagViewerOpenOnStartup=%s\nrunIde\n--args\n%s\n' "$SAMPLE_ABS" "$ROOT_DIR"
  fi
  exit 0
fi

if [[ -n "${PLATFORM_LOCAL_PATH:-}" ]]; then
  exec ./gradlew "-PplatformLocalPath=$PLATFORM_LOCAL_PATH" "-PpagViewerOpenOnStartup=$SAMPLE_ABS" runIde --args "$ROOT_DIR"
fi

exec ./gradlew "-PpagViewerOpenOnStartup=$SAMPLE_ABS" runIde --args "$ROOT_DIR"
