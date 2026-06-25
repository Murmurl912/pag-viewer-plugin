#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VIEWER_HTML="$ROOT_DIR/tools/web-pag-viewer/index.html"
SERVER_SCRIPT="$ROOT_DIR/scripts/serve-web-viewer.sh"

if [[ ! -f "$VIEWER_HTML" ]]; then
  echo "Missing web PAG viewer: $VIEWER_HTML" >&2
  exit 1
fi

if [[ ! -x "$SERVER_SCRIPT" ]]; then
  echo "Missing executable web viewer server script: $SERVER_SCRIPT" >&2
  exit 1
fi

assert_contains() {
  local file="$1"
  local needle="$2"
  if ! grep -Fq "$needle" "$file"; then
    echo "Expected $file to contain: $needle" >&2
    exit 1
  fi
}

assert_not_contains() {
  local file="$1"
  local needle="$2"
  if grep -Fq "$needle" "$file"; then
    echo "Expected $file to not contain: $needle" >&2
    exit 1
  fi
}

assert_contains "$VIEWER_HTML" "cdn.jsdelivr.net/npm/libpag@"
assert_contains "$VIEWER_HTML" "<title>PAG Web Viewer</title>"
assert_contains "$VIEWER_HTML" "type=\"module\""
assert_contains "$VIEWER_HTML" "lib/libpag.esm.js"
assert_contains "$VIEWER_HTML" "await import(sdkModuleUrl)"
assert_contains "$VIEWER_HTML" "PAG.PAGFile.load"
assert_contains "$VIEWER_HTML" "PAG.PAGView.init"
assert_contains "$VIEWER_HTML" "accept=\".pag\""
assert_contains "$VIEWER_HTML" "id=\"pag-file\""
assert_contains "$VIEWER_HTML" "id=\"pag-folder\""
assert_contains "$VIEWER_HTML" "webkitdirectory"
assert_contains "$VIEWER_HTML" "id=\"playlist\""
assert_contains "$VIEWER_HTML" "id=\"playlist-items\""
assert_contains "$VIEWER_HTML" "playlist-empty"
assert_contains "$VIEWER_HTML" "<aside class=\"sidebar\">"
assert_contains "$VIEWER_HTML" "<section id=\"playlist\" class=\"playlist\""
assert_contains "$VIEWER_HTML" "html,"
assert_contains "$VIEWER_HTML" "height: 100%;"
assert_contains "$VIEWER_HTML" "overflow: hidden;"
assert_contains "$VIEWER_HTML" "height: 100vh;"
assert_contains "$VIEWER_HTML" "overflow-y: auto;"
assert_contains "$VIEWER_HTML" "main {"
assert_contains "$VIEWER_HTML" "folder selected"
assert_contains "$VIEWER_HTML" "playlist item clicked"
assert_contains "$VIEWER_HTML" "id=\"play-pause\""
assert_contains "$VIEWER_HTML" "id=\"progress\""
assert_contains "$VIEWER_HTML" "[PAG Web]"
assert_contains "$VIEWER_HTML" "setRepeatCount(0)"
assert_contains "$VIEWER_HTML" "startPlayback(\"auto loop\")"
assert_contains "$VIEWER_HTML" "auto loop playback started"
assert_contains "$VIEWER_HTML" "addEventListener(\"dragover\""
assert_contains "$VIEWER_HTML" "addEventListener(\"drop\""
assert_contains "$VIEWER_HTML" ".drop-zone.drag-over"
assert_contains "$VIEWER_HTML" "drop file selected"
assert_contains "$SERVER_SCRIPT" "python3 -m http.server"
assert_not_contains "$VIEWER_HTML" "id=\"comparison-notes\""
assert_not_contains "$VIEWER_HTML" "id=\"diagnostic-log\""
assert_not_contains "$VIEWER_HTML" "<h2>Compare</h2>"
assert_not_contains "$VIEWER_HTML" "<h2>Log</h2>"
assert_not_contains "$VIEWER_HTML" "PAG Web Comparator"
