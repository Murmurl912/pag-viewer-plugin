#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEY="$("$ROOT_DIR/scripts/native-runtime-key.sh")"
LIBPAG_SHA="$(git -C "$ROOT_DIR/reference/libpag" rev-parse --short=12 HEAD)"

if [[ ! "$KEY" =~ ^libpag-${LIBPAG_SHA}-[0-9a-f]{16}$ ]]; then
  echo "Unexpected native runtime key: $KEY" >&2
  exit 1
fi
