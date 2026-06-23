# PAG Viewer Plugin

Native-backed IntelliJ Platform plugin for previewing Portable Animated Graphics (`.pag`) animation files in IntelliJ IDEA and Android Studio.

## Current Status

- Uses Tencent/libpag through its public C ABI.
- Does not use JCEF or the Web/WASM player.
- Registers `.pag` as a binary file type and opens a custom Swing preview editor.
- Provides play/pause, frame scrubbing, dimensions, frame count, FPS, and native-load status.
- Bundles a macOS arm64 `libpag.dylib` built from `reference/libpag`.

## Build

```bash
./gradlew test buildPlugin
```

The plugin ZIP is generated at:

```text
build/distributions/pag-viewer-plugin-0.1.0.zip
```

## GitHub Release Build

The `Build Release` workflow runs on pushes, pull requests, manual dispatches, and `v*` tags. It builds Linux x86_64, Windows x86_64, and macOS x86_64 libpag C-ABI runtimes, stages them alongside the checked-in macOS arm64 runtime, builds with a downloaded IntelliJ Platform by passing `-PuseRemotePlatform=true`, verifies the final ZIP contains every runtime, uploads the plugin ZIP as a workflow artifact, and publishes the ZIP to a GitHub Release when the pushed ref is a tag such as `v0.1.0`.

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Verify Packaged Native Decode

This checks the built ZIP, not just Gradle's source classpath. It extracts the plugin, verifies JNA is not bundled, loads the bundled `libpag.dylib` using the IDE's platform JNA runtime, and decodes libpag's sample PAG file.

```bash
scripts/verify-artifact.sh
```

The sandbox launcher has a dry-run regression check:

```bash
scripts/test-run-sandbox-sample.sh
```

## Launch Sandbox For Human Verification

This launches the IntelliJ sandbox with the plugin loaded. The script opens the project root and passes the sample PAG path through `-PpagViewerOpenOnStartup`, which becomes `-Dpag.viewer.open.on.startup=...` inside the sandbox IDE. The plugin startup hook then opens that PAG file in the custom editor.

```bash
scripts/run-sandbox-sample.sh
```

To launch against Android Studio instead of the default `platformLocalPath`:

```bash
PLATFORM_LOCAL_PATH="/Applications/Android Studio.app" scripts/run-sandbox-sample.sh
```

You can also pass any local PAG file:

```bash
scripts/run-sandbox-sample.sh /path/to/animation.pag
```

For log-only smoke verification, confirm `idea.log` contains `Loaded custom plugins: PAG Viewer`, `PAG preview ready: ..., decoder=..., composition=..., frames=72`, and `PAG verification file open completed: ..., editors=1`. Visual playback and scrubbing still require a human check on this device.

## Web PAG Viewer

The repo includes a small static viewer that uses the official libpag Web SDK from CDN. It is separate from the IntelliJ plugin and exists as a browser reference while validating the native viewer.

```bash
scripts/serve-web-viewer.sh
```

Open:

```text
http://localhost:8090/tools/web-pag-viewer/
```

Use `Sample` to load libpag's bundled `frames.pag`, select or drag a single local `.pag` file, or use `Folder` to add every `.pag` file from a local folder into the left playlist. Each loaded PAG starts playing automatically in an infinite loop, so you can compare dimensions, playback, and slider scrubbing.

The web viewer writes diagnostics to the browser console with a `[PAG Web]` prefix. The plugin writes matching lifecycle and interaction diagnostics to the IDE log with `PAG preview`, `PAG playback`, and `PAG render` prefixes.

## Install For Human Verification

1. Open IntelliJ IDEA or Android Studio.
2. Go to `Settings | Plugins`.
3. Choose `Install Plugin from Disk...`.
4. Select `build/distributions/pag-viewer-plugin-0.1.0.zip`.
5. Restart the IDE if prompted.
6. Open a real `.pag` file and confirm preview playback and scrubbing.

## Limitations

- The source tree only commits the macOS arm64 runtime. The GitHub release workflow builds and stages Windows x86_64, Linux x86_64, and macOS x86_64 runtimes before packaging the release ZIP.
- Windows/Linux target-machine smoke testing is still needed after the first complete CI release ZIP is produced.
- Audio playback, editable text/image replacement, layer inspection, timeline markers, and PAGViewer-style profiling are not implemented yet.
