# Build Tasks: Native PAG Viewer IntelliJ Plugin

Generated from: user request on 2026-06-22

## Foundation
- [x] **Repository and source reference**: Initialize this directory as a git repository and add Tencent/libpag as `reference/libpag` for local source reference. _Reuses: official Apache-2.0 libpag source as a submodule._
- [x] **Build scaffold**: Create a Gradle-based IntelliJ plugin project using Java, JUnit, JNA, and IntelliJ Platform Gradle Plugin 2.x. _Creates: plugin build and test scaffold._
- [x] **Porting memo**: Track covered source APIs, unsupported areas, and all deviations from libpag/Android/Web source. _Creates: `docs/PORTING_MEMO.md`._

## Core Preview
- [x] **Native C bridge boundary**: Wrap libpag C API calls for file load, decoder creation, metadata, frame reads, and release. _Reuses: `include/pag/c/pag_file.h`, `pag_decoder.h`, `pag_types.h`._
- [x] **Frame conversion**: Convert libpag BGRA frame buffers into Swing-compatible `BufferedImage` frames. _Creates: Java image conversion layer._
- [x] **Editor integration**: Register `.pag` file type and `FileEditorProvider` so PAG files open in a custom preview editor. _Creates: IntelliJ plugin UI classes._
- [x] **Playback UI**: Add play/pause, frame slider, frame count, FPS, dimensions, and native-load status. _Creates: Swing editor panel._
- [x] **Image-viewer controls**: Match the built-in image-preview shape with chessboard and grid toggles, zoom in/out, actual-size, fit zoom, bottom playback controls, autoplay, playback speed from `0.25x` to `2x`, hidden-tab stop, and playback performance diagnostics. _Creates: scrollable/zoomable Swing canvas and performance-aware playback strip._
- [x] **Toolbar toggle clarity**: Replace the alpha-icon proxy with a real chessboard icon and give the chessboard/grid toggles clear selected-state icons plus show/hide tooltips. _Fixes: ambiguous alpha button and invisible toggle state._
- [x] **Single transport button**: Collapse play/stop into one bottom-bar button and keep playback FPS diagnostics out of the visible control area. _Fixes: duplicated transport controls and noisy playback status text._
- [x] **Small asset quality fix**: Keep fit zoom from upscaling small decoded PAG frames past `1x`, avoiding raster blur on assets like `guide_hand_drag.pag`. _Fixes: quality loss caused by enlarging a 240px decoded frame to the full editor viewport._
- [x] **CPU playback optimization**: Reuse the native pixel buffer, lazily cache decoded frames when the animation fits a bounded memory budget, skip unchanged frames via libpag, and stream uncached large animations through a small reusable image ring. _Fixes: avoidable per-frame decode, allocation, and copy churn in the first Swing renderer._
- [x] **Android Studio mirroring research**: Inspect Android Studio's device mirroring implementation and document what can be borrowed for PAG playback. _Creates: `docs/ANDROID_STUDIO_MIRRORING_RESEARCH.md`._
- [x] **Swing display buffering**: Add a local volatile-image buffering painter and use fast rendering during playback with a delayed high-quality repaint when frames settle. _Borrows: Android Studio mirroring display pattern without depending on Android plugin internals._
- [x] **Decode-ahead playback cache**: Add a background decode-ahead worker that fills the bounded frame cache for upcoming playback frames and cancels stale prefetch work on scrub, stop, or newer playback frames. _Improves: playback smoothness on cacheable animations without racing one native decoder across multiple threads._
- [x] **Image-viewer interaction polish**: Change the transport button to Play/Pause without rewinding, tighten the top toolbar grouping, zoom toolbar actions around the viewport center, keep small manually-zoomed images centered in the viewport, support control/meta wheel zoom around the cursor focal point, use borderless toolbar/transport icon styling, draw the seek thumb as a rounded dot, remove the persistent `Ready` label, and use dark-theme-safe checkerboard colors. _Fixes: editor behavior mismatches found during release verification._
- [ ] **Parallel decoder prototype**: Evaluate multiple independent libpag decoder instances for parallel frame predecode behind a feature flag. _Open question: native memory cost, source cache interactions, and whether parallel decode improves real PAG assets enough to justify complexity._

## Native Packaging
- [x] **Local macOS native build**: Build or locate a `libpag` dynamic library from the submodule and package it under plugin resources when available. _Reuses: libpag CMake and C ABI._
- [x] **Native lookup fallback**: Support `-Dpag.viewer.libpag.path=/path/to/libpag` and `PAG_VIEWER_LIBPAG_PATH` for development verification. _Creates: explicit verifier path._
- [x] **Packaged native reuse**: Reuse the extracted packaged `libpag` path within one IDE process to avoid duplicate native class registration when multiple PAG previews are opened.
- [x] **GitHub release workflow**: Add CI that builds/tests the plugin with a downloaded IntelliJ Platform, verifies the ZIP structure, uploads workflow artifacts, and publishes a GitHub Release for `v*` tags.
- [x] **Native runtime CI probe**: Add a manual workflow and scripts that attempt Linux x86_64 and Windows x86_64 libpag C-ABI shared-library builds. _Status: workflow is available for GitHub-hosted target runners; artifacts are not yet folded into the plugin ZIP._
- [ ] **Full multi-platform plugin ZIP**: After Linux/Windows native runtime workflow artifacts are verified on target IDEs, stage them under `src/main/resources/native/<platform>/` and extend packaged smoke tests per OS.

## Review
- [x] **Automated verification**: Run unit tests and plugin build tasks.
- [x] **Editor panel verification**: Exercise the Swing preview panel against a real PAG sample and verify it reaches a ready decoded state with expected metadata.
- [x] **Scrub render verification**: Programmatically move the frame slider on a real PAG sample and verify the rendered canvas pixels change.
- [x] **Packaged artifact verification**: Verify the built plugin ZIP contains the plugin jar, patched plugin XML, macOS arm64 native library, no bundled JNA jar, and can decode a real `.pag` file using the IntelliJ platform JNA runtime.
- [x] **Sandbox launch helper**: Provide a script that launches a Gradle IDE sandbox with the plugin loaded and opens a sample or user-provided `.pag` file through an opt-in startup verification hook.
- [x] **Sandbox launch smoke**: Run the IntelliJ sandbox long enough to confirm the PAG Viewer plugin loads, the sample `.pag` path reaches the IDE JVM, and the startup hook opens `frames.pag` with one custom editor.
- [x] **Sandbox issue follow-up**: Fix the sandbox-observed platform issues: the `HIDE_DEFAULT_EDITOR` provider is now `DumbAware`, and the startup helper resolves the VFS file off the EDT before opening the editor on the UI thread.
- [x] **Sandbox runtime decode smoke**: Confirm the real IDE process logs `PAG preview ready` with sample decoder metadata and `72` frames after opening the sample `.pag` editor.
- [x] **Web comparison helper**: Add a standalone browser comparator that uses the official libpag Web SDK so the native plugin output can be compared against a known upstream renderer.
- [x] **Comparison diagnostics**: Add web console/on-page logs and plugin `idea.log` diagnostics for load, playback, stop, and manual scrub/render events.
- [x] **Playback behavior verification**: Cover chessboard/grid/zoom state, selected-state toolbar toggle icons, single bottom transport button placement, autoplay, speed-adjusted timer delay, hidden-tab playback stop, and absence of visible playback-FPS text in Swing panel tests.
- [x] **Fit quality regression**: Verify fit mode centers small decoded frames at native size instead of upscaling them.
- [x] **CPU performance regression tests**: Verify cached frames avoid repeat native decode, unchanged frames reuse the prior image, and uncached streams reuse image buffers.
- [x] **Mirroring architecture review**: Confirm Android Studio mirroring uses FFmpeg-decoded `BufferedImage` frames plus Swing/volatile-image painting rather than a public reusable texture component.
- [x] **Buffered display regression**: Verify playback frames enter the fast paint path and schedule a deferred high-quality repaint, while still/scrubbed frames paint in high-quality mode immediately.
- [x] **Decode-ahead regression**: Verify cacheable frames can be preloaded for later playback reads and uncached streaming mode refuses predecode instead of mutating reusable paint buffers.
- [x] **Interaction polish regression**: Verify Play/Pause does not rewind, toolbar zoom preserves viewport center, small manually-zoomed images track the viewport for centering, wheel zoom preserves cursor focal point, toolbar and transport buttons use icon-button chrome, the slider uses a round thumb UI, the persistent `Ready` label is absent, and checkerboard contrast remains visible on dark backgrounds.
- [ ] **Human verification checkpoint**: Stop once the first native-backed viewer is available and ask the user to open a real `.pag` file in IntelliJ/Android Studio.
