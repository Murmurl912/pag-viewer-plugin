#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_VERSION="$(awk -F= '/^pluginVersion=/ {print substr($0, index($0, "=") + 1)}' "$ROOT_DIR/gradle.properties")"
ZIP_PATH="${1:-"$ROOT_DIR/build/distributions/pag-viewer-plugin-${PLUGIN_VERSION}.zip"}"
REQUIRED_PLATFORMS="${2:-macos-aarch64}"

if [[ ! -f "$ZIP_PATH" ]]; then
  echo "Plugin ZIP not found: $ZIP_PATH" >&2
  echo "Run: ./gradlew buildPlugin" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

unzip -q "$ZIP_PATH" -d "$TMP_DIR/plugin"

PLUGIN_JAR="$(find "$TMP_DIR/plugin" -path '*/lib/pag-viewer-plugin-*.jar' -print -quit)"
if [[ -z "$PLUGIN_JAR" ]]; then
  echo "Plugin jar missing from ZIP." >&2
  exit 1
fi

if find "$TMP_DIR/plugin" -path '*/lib/jna-*.jar' -print -quit | grep -q .; then
  echo "Plugin ZIP must not bundle jna-*.jar; IntelliJ supplies JNA and native dispatch." >&2
  exit 1
fi

unzip -p "$PLUGIN_JAR" META-INF/plugin.xml | grep -q 'since-build="252"'
unzip -p "$PLUGIN_JAR" META-INF/plugin.xml | grep -q "<version>${PLUGIN_VERSION}</version>"

IFS=',' read -r -a platforms <<< "$REQUIRED_PLATFORMS"
for platform in "${platforms[@]}"; do
  case "$platform" in
    macos-aarch64|macos-x86_64)
      library="native/${platform}/libpag.dylib"
      ;;
    linux-x86_64)
      library="native/${platform}/libpag.so"
      ;;
    windows-x86_64)
      library="native/${platform}/pag.dll"
      ;;
    *)
      echo "Unknown required native platform: $platform" >&2
      exit 1
      ;;
  esac

  zipinfo -1 "$PLUGIN_JAR" | grep -Fxq "$library" || {
    echo "Missing required native runtime: $library" >&2
    exit 1
  }
done

if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$ZIP_PATH"
else
  sha256sum "$ZIP_PATH"
fi
