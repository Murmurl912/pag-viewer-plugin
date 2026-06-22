#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_VERSION="$(awk -F= '/^pluginVersion=/ {print substr($0, index($0, "=") + 1)}' "$ROOT_DIR/gradle.properties")"
ZIP_PATH="${1:-"$ROOT_DIR/build/distributions/pag-viewer-plugin-${PLUGIN_VERSION}.zip"}"
SAMPLE_PATH="${2:-"$ROOT_DIR/reference/libpag/web/lite/demo/assets/frames.pag"}"
CONFIGURED_PLATFORM_PATH="$(awk -F= '/^platformLocalPath=/ {print substr($0, index($0, "=") + 1)}' "$ROOT_DIR/gradle.properties")"
PLATFORM_PATH="${PLATFORM_LOCAL_PATH:-"$CONFIGURED_PLATFORM_PATH"}"

if [[ -z "$PLATFORM_PATH" ]]; then
  for candidate in \
    "/Applications/Android Studio.app" \
    "/Applications/IntelliJ IDEA.app" \
    "$HOME/Applications/IntelliJ IDEA.app"; do
    if [[ -d "$candidate" ]]; then
      PLATFORM_PATH="$candidate"
      break
    fi
  done
fi

PLATFORM_JNA_JAR="$PLATFORM_PATH/Contents/lib/util-8.jar"
PLATFORM_JNA_NATIVE_DIR="$PLATFORM_PATH/Contents/lib/jna/aarch64"

if [[ ! -f "$ZIP_PATH" ]]; then
  echo "Plugin ZIP not found: $ZIP_PATH" >&2
  echo "Run: ./gradlew buildPlugin" >&2
  exit 1
fi

if [[ ! -f "$SAMPLE_PATH" ]]; then
  echo "Sample PAG not found: $SAMPLE_PATH" >&2
  echo "Run dependency sync for reference/libpag first." >&2
  exit 1
fi

if [[ ! -f "$PLATFORM_JNA_JAR" || ! -d "$PLATFORM_JNA_NATIVE_DIR" ]]; then
  echo "Platform JNA runtime not found under: $PLATFORM_PATH" >&2
  echo "Set PLATFORM_LOCAL_PATH to an IntelliJ IDEA or Android Studio app path." >&2
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
unzip -l "$PLUGIN_JAR" | grep -q 'native/macos-aarch64/libpag.dylib'

mkdir -p "$TMP_DIR/classes"
javac -cp "$PLUGIN_JAR:$PLATFORM_JNA_JAR" "$ROOT_DIR/scripts/PagArtifactSmoke.java" -d "$TMP_DIR/classes"
java \
  -Djna.boot.library.path="$PLATFORM_JNA_NATIVE_DIR" \
  -Djna.noclasspath=true \
  -Djna.nosys=true \
  -cp "$TMP_DIR/classes:$PLUGIN_JAR:$PLATFORM_JNA_JAR" \
  PagArtifactSmoke "$SAMPLE_PATH"
