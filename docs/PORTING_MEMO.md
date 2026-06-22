# PAG Viewer Porting Memo

Date: 2026-06-22

## Source References

- `reference/libpag/include/pag/c/pag_file.h`: used for `pag_file_load()` and editable metadata counts.
- `reference/libpag/include/pag/c/pag_decoder.h`: used for decoder creation, size, frame count, frame rate, and frame reads.
- `reference/libpag/include/pag/c/pag_types.h`: used for `pag_release()`, `pag_color_type_bgra_8888`, and `pag_alpha_type_unpremultiplied`.
- `reference/libpag/android/libpag/src/main/java/org/libpag/PAGFile.java`: confirms Android Java APIs are JNI wrappers over native libpag, not a pure Java decoder.
- `reference/libpag/web/lite/src`: retained as a decoder/rendering reference, but not ported for the first viewer.
- `reference/libpag/web/demo/index.ts`: used to confirm the Web SDK comparison path loads `File | ArrayBuffer | Blob` with `PAG.PAGFile.load()` and creates a canvas renderer with `PAG.PAGView.init()`.

## Current Port Scope

Covered:

- IntelliJ file type registration for `.pag`.
- Custom file editor registration for `.pag`.
- Native API seam via the public libpag C ABI.
- Frame metadata model: width, height, frame count, frame rate.
- CPU frame decode path using `pag_decoder_read_frame()`.
- Swing playback surface using decoded `BufferedImage` frames.
- Metadata display for both decoder output size and source composition size when they differ.

Not covered yet:

- Text or image replacement APIs.
- Audio playback.
- Embedded video-specific controls beyond what libpag decoder exposes as frames.
- Performance/profiling panel equivalent to PAGViewer.
- Timeline markers, layer tree, editable object inspector, or export tools.
- Cross-platform bundled native libraries for Windows/Linux/macOS Intel.

## Implementation Deviations From Source

- This plugin does not use JCEF or the Web/WASM player because the requested direction excludes JCEF for performance reasons.
- This plugin does not port the Android Java API classes directly. Those classes depend on Android runtime types and native JNI loading utilities, so the plugin instead targets the public C ABI through JNA.
- This plugin decodes CPU frames with `pag_decoder_read_frame()` and paints Swing images. Official macOS PAGViewer uses native AppKit views and GPU surfaces.
- `PAGDecoder::MakeFrom()` can clamp decoder output size below composition size for some files. The plugin therefore logs both decoder size and composition size, and the UI shows both when they differ.
- The first viewer uses `BGRA_8888` plus unpremultiplied alpha so Java can read little-endian pixels into `TYPE_INT_ARGB` with minimal channel shuffling.
- Native library discovery supports explicit development paths before packaged resources. This allows verification while native packaging is still being made reproducible.
- Packaged native library extraction is cached per IDE process so multiple PAG previews load the same dylib path instead of registering duplicate macOS Objective-C classes from repeated temp copies.
- JNA is compile-time only for the plugin and is intentionally not bundled. IntelliJ IDEA and Android Studio start with `-Djna.noclasspath=true` and `-Djna.nosys=true`, so the plugin must use the platform-provided JNA classes from the IDE and native dispatch from `Contents/lib/jna/<arch>`.
- A test-only/startup-verification opener is available through `-Dpag.viewer.open.on.startup=/path/to/file.pag` or `PAG_VIEWER_OPEN_ON_STARTUP=/path/to/file.pag`. Normal plugin usage is still file-editor based; the hook is only for sandbox verification where the project root is opened first.
- The default Gradle build targets a local IntelliJ IDEA installation through `platformLocalPath` instead of downloading a full IDE artifact during local development. Use `-PplatformLocalPath="/Applications/Android Studio.app"` to compile against Android Studio APIs during compatibility checks.
- The first packaged native artifact is macOS arm64 only: `src/main/resources/native/macos-aarch64/libpag.dylib`, built locally from `reference/libpag` with `PAG_USE_C=ON`, `PAG_BUILD_SHARED=ON`, tests/framework/CLI off.
- `tools/web-pag-viewer` is not plugin code. It is a standalone validation aid that uses the official `libpag@4.5.3` Web SDK from CDN so a human can compare the native Swing viewer against the upstream browser renderer. It emits `[PAG Web]` browser-console logs.
- The plugin emits IDE-side diagnostics for load start, native library resolution, byte read, first frame decode, ready/failure, playback start/stop, manual scrub requests, and manual render completion/failure. Continuous playback frame rendering is intentionally not logged frame-by-frame to keep `idea.log` usable.

## Verification Notes

- Automated tests cover frame timing, native library path resolution, BGRA-to-ARGB frame conversion, IntelliJ `.pag` file provider acceptance, the `DumbAware` requirement for hiding the default editor, startup verification scheduling, Swing preview panel load-to-ready behavior, slider-driven scrub rendering with changed canvas pixels, and a macOS arm64 native smoke decode of `reference/libpag/web/lite/demo/assets/frames.pag`.
- `./gradlew test buildPlugin` passed on 2026-06-22 using local IntelliJ IDEA from `platformLocalPath`.
- `./gradlew clean test buildPlugin -PplatformLocalPath="/Applications/Android Studio.app"` passed on 2026-06-22 against Android Studio 2025.2 (`AI-252.25557.131.2521.14432022`).
- `./gradlew verifyPluginStructure` passed on 2026-06-22.
- Plugin XML is patched with `sinceBuild=252` so it can install into the local Android Studio 2025.2 build (`AI-252...`) as well as IntelliJ IDEA 2025.3 (`IU-253...`).
- Plugin artifact: `build/distributions/pag-viewer-plugin-0.1.0-SNAPSHOT.zip`.
- Artifact smoke check: `scripts/verify-artifact.sh` loads classes from the built plugin ZIP plus platform JNA from `platformLocalPath`, confirms patched `plugin.xml`, confirms `native/macos-aarch64/libpag.dylib` is present, confirms no `jna-*.jar` is bundled in the plugin ZIP, extracts the native library from the plugin jar, and decodes `reference/libpag/web/lite/demo/assets/frames.pag` with non-empty decoder dimensions, composition dimensions, `72` frames.
- Sandbox human-verification helper: `scripts/run-sandbox-sample.sh` runs `runIde` with the plugin loaded, opens the project root, and passes libpag's sample PAG file or a caller-provided `.pag` path through `-PpagViewerOpenOnStartup`. Set `PLATFORM_LOCAL_PATH="/Applications/Android Studio.app"` to launch the Android Studio sandbox.
- Sandbox launcher regression: `scripts/test-run-sandbox-sample.sh` verifies default and Android Studio dry-run command construction.
- Bounded IntelliJ sandbox launch on 2026-06-22 used log-only verification because screen capture is unavailable on this device. `idea.log` confirmed `-Dpag.viewer.open.on.startup=/Users/wen_zhao/opensource/pag-viewer-plugin/reference/libpag/web/lite/demo/assets/frames.pag`, `Loaded custom plugins: PAG Viewer (0.1.0-SNAPSHOT)`, `PAG preview ready: ..., decoder=202x202, frames=72, fps=24.00`, `PAG verification file open requested`, and `PAG verification file open completed: ..., editors=1`. Newer diagnostics also include source `composition=600x600` for the bundled sample, matching the Web SDK's file metadata.
- The same bounded sandbox log scan found no matches for the previous plugin-side signatures `Unable to locate JNA native support library`, `HIDE_DEFAULT_EDITOR`, `Slow operations are prohibited`, plugin-owned `WARN`/`ERROR`, or `SEVERE` entries after the follow-up fixes.
- `scripts/test-web-viewer.sh` verifies the static comparator is present, executable through `scripts/serve-web-viewer.sh`, wired to the official Web SDK load/render controls, and includes browser/on-page diagnostics.
- Human verification is still required with real PAG files to confirm visual playback, play/pause behavior, and scrubbing inside IntelliJ IDEA or Android Studio.
