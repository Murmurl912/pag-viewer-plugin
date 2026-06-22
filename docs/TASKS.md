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

## Native Packaging
- [x] **Local macOS native build**: Build or locate a `libpag` dynamic library from the submodule and package it under plugin resources when available. _Reuses: libpag CMake and C ABI._
- [x] **Native lookup fallback**: Support `-Dpag.viewer.libpag.path=/path/to/libpag` and `PAG_VIEWER_LIBPAG_PATH` for development verification. _Creates: explicit verifier path._
- [x] **Packaged native reuse**: Reuse the extracted packaged `libpag` path within one IDE process to avoid duplicate native class registration when multiple PAG previews are opened.

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
- [ ] **Human verification checkpoint**: Stop once the first native-backed viewer is available and ask the user to open a real `.pag` file in IntelliJ/Android Studio.
