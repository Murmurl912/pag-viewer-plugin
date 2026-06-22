# Changelog

## 0.1.0 - 2026-06-22

Initial local release.

- Adds a native-backed `.pag` file preview editor for IntelliJ IDEA and Android Studio.
- Uses Tencent/libpag through the public C ABI via JNA; JCEF/WebView is not used.
- Bundles macOS arm64 `libpag.dylib`.
- Supports autoplay, Play/Pause, frame scrubbing, playback speed from `0.25x` to `2x`, and stop-on-hidden-tab behavior.
- Adds image-viewer controls: chessboard, grid, zoom in/out, actual size, fit zoom, viewport-center toolbar zoom, and focal-point wheel zoom.
- Adds dark-theme-safe checkerboard rendering, rounded seek thumb, tight icon toolbar, and borderless transport button styling.
- Adds CPU playback optimizations: reusable pixel buffer, bounded decoded-frame cache, unchanged-frame reuse, streaming image ring, volatile-image paint buffering, and decode-ahead cache warmup.
- Includes `tools/web-pag-viewer` for visual comparison against the official libpag Web SDK.

Known limitations:

- Native runtime is macOS arm64 only.
- Marketplace signing/publishing is not configured.
- Audio playback, editable text/image replacement, layer inspection, timeline markers, and PAGViewer-style profiling are not implemented.
- True parallel decode and GPU texture rendering are future work.
