#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-8090}"

cd "$ROOT_DIR"
echo "PAG web comparator: http://localhost:$PORT/tools/web-pag-viewer/"
python3 -m http.server "$PORT"
