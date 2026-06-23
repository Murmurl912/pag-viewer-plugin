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
- Image-viewer style canvas controls: chessboard transparency background, grid overlay, zoom in/out, actual-size, and fit zoom.
- The chessboard and grid toolbar toggles use explicit selected-state icons plus show/hide tooltips, rather than the earlier alpha-icon proxy.
- Bottom playback controls with autoplay, a single Play/Pause transport button, scrub slider, and `0.25x` through `2x` playback speed.
- Runtime playback diagnostics: measured playback FPS, latest decode time, dropped playback ticks, and throttled slow-frame logs are emitted to `idea.log`, not shown in the playback control area.
- Fit zoom is shrink-only. Small decoded animations are centered at native pixel size instead of being enlarged to the editor viewport, avoiding blur on low-resolution PAG assets such as `guide_hand_drag.pag`.
- CPU playback optimizations for the first Swing renderer: reusable direct pixel read buffer, direct writes into `BufferedImage` raster data, bounded lazy decoded-frame cache for small/medium animations, libpag `pag_decoder_check_frame_changed()` reuse for unchanged frames, and a reusable image ring for uncached large animations.
- Display-path optimization borrowed from Android Studio mirroring: decoded frames are painted through a local `VolatileImage` buffer, playback frames use cheaper rendering hints, and the canvas schedules a delayed high-quality repaint when animation stops or settles.
- Decode-ahead playback optimization: a dedicated background worker preloads upcoming playback frames into the existing bounded frame cache. Stale prefetch batches are canceled when the user scrubs, stops playback, or a newer playback frame supersedes the batch.
- Image-viewer interaction polish: toolbar controls are tightly grouped, toolbar zoom keeps the viewport center stable, control/meta mouse-wheel or trackpad wheel zoom keeps the cursor focal point stable, the checkerboard uses dark-theme-safe colors, toolbar and transport buttons use borderless icon-button chrome, the seek thumb is a rounded dot, and the bottom bar no longer shows a persistent `Ready` label.

Not covered yet:

- Text or image replacement APIs.
- Audio playback.
- Embedded video-specific controls beyond what libpag decoder exposes as frames.
- Full performance/profiling panel equivalent to PAGViewer.
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
- The upstream `reference/libpag/viewer` application confirms an official Qt desktop viewer path for macOS and Windows, but that build produces a full PAGViewer app with Qt, updater, exporter, and packaging dependencies. The plugin does not need that app stack; it needs only the smaller libpag C ABI shared runtime. CI therefore uses the root `build_pag` path with `PAG_USE_C=ON` and `PAG_BUILD_SHARED=ON` for Linux, Windows, and macOS x86_64 runtime artifacts.
- `Build Release` GitHub Actions workflow builds/tests the plugin using a downloaded IntelliJ Platform (`-PuseRemotePlatform=true`), stages Linux x86_64, Windows x86_64, macOS x86_64, and checked-in macOS arm64 native runtimes, verifies the ZIP contains every runtime, uploads the ZIP as a workflow artifact, and attaches it to GitHub Releases for `v*` tags.
- `tools/web-pag-viewer` is not plugin code. It is a standalone validation aid that uses the official `libpag@4.5.3` Web SDK from CDN so a human can compare the native Swing viewer against the upstream browser renderer. It emits `[PAG Web]` browser-console logs.
- The plugin emits IDE-side diagnostics for load start, native library resolution, byte read, first frame decode, ready/failure, playback start/stop, manual scrub requests, manual render completion/failure, and sampled playback performance. Continuous playback frame rendering is intentionally not logged frame-by-frame to keep `idea.log` usable.
- Playback is timer-driven on the Swing event thread and decoding remains serialized on a single background executor. If a playback tick arrives while decode is busy, the tick is dropped rather than queued, keeping UI latency bounded at the cost of possible frame skips on expensive PAG files.
- Decoded-frame caching is enabled only when the full animation fits the `pag.viewer.frame.cache.bytes` budget, defaulting to `96MB`. Set that system property to `0` to force streaming mode during profiling.
- Decode-ahead is controlled by `pag.viewer.decodeAhead.frames`, defaulting to `6`. It only runs when the bounded decoded-frame cache is enabled. It does not preload streaming-mode animations because those frames reuse a small mutable image ring that may still be painted by Swing.
- The current decode-ahead worker runs on a second Java thread but still serializes access to one native `PAGDecoder` instance and direct pixel buffer. True parallel predecode would require separate native decoder instances per worker; that remains a prototype task because sharing one decoder across threads would risk native state races.
- The Android Studio mirroring research did not produce a reusable public texture component. Instead, this plugin adapts the safe part of that architecture locally: Swing paints decoded `BufferedImage` frames through a `VolatileImage` buffer and separates fast playback painting from higher-quality still-frame painting.
- Initial load also uses the plugin-owned executor and posts completion back to Swing explicitly instead of `SwingWorker`. This avoids platform test-harness timer leaks and keeps native decode work serialized with playback reads.
- Unlike the first minimal preview, playback now starts automatically once the file is ready. The bottom transport is a single Play/Pause button: Pause keeps the current frame, and switching away from the editor tab also stops playback without rewinding or automatically resuming.
- The Web SDK can render vector PAG content at the target canvas resolution. The first native Swing viewer currently decodes fixed-size CPU frames, so enlarging those frames in Swing causes raster interpolation blur. The viewer therefore defaults fit zoom to no larger than `1x`; higher manual zoom remains available for inspection.

## Verification Notes

- Automated tests cover frame timing, native library path resolution, BGRA-to-ARGB frame conversion, decoded-frame cache reuse, unchanged-frame decode skipping, cache-backed decode-ahead preloading, uncached streaming image reuse, IntelliJ `.pag` file provider acceptance, the `DumbAware` requirement for hiding the default editor, startup verification scheduling, Swing preview panel load-to-ready behavior, slider-driven scrub rendering with changed canvas pixels, image-viewer controls, visible selected-state chessboard/grid toggles, tight toolbar layout, toolbar and transport icon-button styling, rounded seek thumb UI, absence of the persistent `Ready` label, dark-background checkerboard contrast, viewport-center toolbar zoom, cursor-focal wheel zoom, shrink-only fit zoom for small assets, playback-frame deferred high-quality repaint scheduling, autoplay, Play/Pause without rewind, playback speed delay changes, hidden-tab playback stop, absence of visible playback-FPS text, and a macOS arm64 native smoke decode of `reference/libpag/web/lite/demo/assets/frames.pag`.
- `./gradlew test buildPlugin` passed on 2026-06-22 using local IntelliJ IDEA from `platformLocalPath`.
- `./gradlew test buildPlugin -PplatformLocalPath="/Applications/Android Studio.app"` passed on 2026-06-22 after the image-viewer playback pass.
- `./gradlew clean test buildPlugin -PplatformLocalPath="/Applications/Android Studio.app"` passed on 2026-06-22 against Android Studio 2025.2 (`AI-252.25557.131.2521.14432022`).
- `./gradlew verifyPluginStructure` passed on 2026-06-22.
- Plugin XML is patched with `sinceBuild=252` so it can install into the local Android Studio 2025.2 build (`AI-252...`) as well as IntelliJ IDEA 2025.3 (`IU-253...`).
- Plugin artifact: `build/distributions/pag-viewer-plugin-0.1.0.zip`.
- Artifact smoke check: `scripts/verify-artifact.sh` loads classes from the built plugin ZIP plus platform JNA from `platformLocalPath`, confirms patched `plugin.xml`, confirms `native/macos-aarch64/libpag.dylib` is present, confirms no `jna-*.jar` is bundled in the plugin ZIP, extracts the native library from the plugin jar, and decodes `reference/libpag/web/lite/demo/assets/frames.pag` with non-empty decoder dimensions, composition dimensions, `72` frames.
- Quality regression smoke: `scripts/verify-artifact.sh build/distributions/pag-viewer-plugin-0.1.0.zip reference/libpag/assets/guide_hand_drag.pag` confirmed `guide_hand_drag.pag` decodes as `decoder=240x240, composition=240x240, frames=60, fps=60.0`; the Swing fit view now keeps that small raster at native size instead of enlarging it.
- Sandbox human-verification helper: `scripts/run-sandbox-sample.sh` runs `runIde` with the plugin loaded, opens the project root, and passes libpag's sample PAG file or a caller-provided `.pag` path through `-PpagViewerOpenOnStartup`. Set `PLATFORM_LOCAL_PATH="/Applications/Android Studio.app"` to launch the Android Studio sandbox.
- Sandbox launcher regression: `scripts/test-run-sandbox-sample.sh` verifies default and Android Studio dry-run command construction.
- Bounded IntelliJ sandbox launch on 2026-06-22 used log-only verification because screen capture is unavailable on this device. `idea.log` confirmed `-Dpag.viewer.open.on.startup=/Users/wen_zhao/opensource/pag-viewer-plugin/reference/libpag/web/lite/demo/assets/frames.pag`, `Loaded custom plugins: PAG Viewer`, `PAG preview ready: ..., decoder=202x202, frames=72, fps=24.00`, `PAG verification file open requested`, and `PAG verification file open completed: ..., editors=1`. Newer diagnostics also include source `composition=600x600` for the bundled sample, matching the Web SDK's file metadata.
- The same bounded sandbox log scan found no matches for the previous plugin-side signatures `Unable to locate JNA native support library`, `HIDE_DEFAULT_EDITOR`, `Slow operations are prohibited`, plugin-owned `WARN`/`ERROR`, or `SEVERE` entries after the follow-up fixes.
- `scripts/test-web-viewer.sh` verifies the static comparator is present, executable through `scripts/serve-web-viewer.sh`, wired to the official Web SDK load/render controls, and includes browser/on-page diagnostics.
- Human verification is still required with real PAG files to confirm visual playback, Play/Pause behavior, toolbar toggle state clarity, focal-point zooming, dark-theme checkerboard contrast, and scrubbing inside IntelliJ IDEA or Android Studio.
