# PAG Viewer

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Murmurl912/pag-viewer-plugin?include_prereleases&sort=semver)](https://github.com/Murmurl912/pag-viewer-plugin/releases)

> Preview `.pag` (Portable Animated Graphics) animation files directly inside IntelliJ IDEA, Android Studio, and other IntelliJ-based IDEs.

PAG Viewer registers `.pag` as a recognized file type and opens it in a dedicated, native-backed preview editor — so you can play, scrub, and inspect PAG animations without leaving your IDE. Rendering is powered by Tencent's [libpag](https://github.com/Tencent/libpag) through its public C ABI (via JNA); it does **not** rely on JCEF, WebView, or the WASM player.

## Features

- **Native PAG playback** — decodes and renders animations with the libpag C library bundled for your platform.
- **Playback controls** — play/pause, looping, frame-accurate scrubbing, and playback speed from 0.25× to 2×. The preview opens paused on the first frame, and playback pauses automatically when the editor tab is hidden.
- **Animation info** — dimensions, frame count, FPS, and native-runtime load status shown at a glance.
- **Viewer controls** — zoom in/out, actual size, fit-to-window, focal-point wheel zoom, plus a dark-theme-safe checkerboard/grid background for inspecting transparency.
- **Smooth on CPU** — buffered, cached frame rendering for fluid playback without requiring a GPU pipeline.
- **Thumbnail file icons** — `.pag` files show a decoded first-frame thumbnail as their icon in the Project view, generated lazily off the UI thread and cached in memory. Toggle with the `pag.viewer.thumbnails` registry key (Help → Find Action → "Registry…").

## Requirements

- IntelliJ IDEA, Android Studio, or another IntelliJ-based IDE on build **252 (2025.2)** or newer.
- A native runtime for your platform. Official release builds bundle runtimes for:
  - macOS (Apple silicon / arm64)
  - Linux (x86_64)
  - Windows (x86_64)

## Installation

1. Download the latest `pag-viewer-plugin-<version>.zip` from the [Releases](https://github.com/Murmurl912/pag-viewer-plugin/releases) page.
2. In your IDE, open **Settings/Preferences → Plugins**.
3. Click the gear icon and choose **Install Plugin from Disk…**.
4. Select the downloaded ZIP, then restart the IDE when prompted.
5. Open any `.pag` file from the Project view to start previewing.

## Usage

Open a `.pag` file and it loads in the PAG Viewer editor:

- Use **▶ / ⏸** to play or pause — the preview opens paused on the first frame and loops while playing.
- Drag the **seek bar** to scrub to any frame.
- Change **playback speed** between 0.25× and 2×.
- **Zoom** with the toolbar (zoom in/out, actual size, fit-to-window) or `Ctrl`/`Cmd` + mouse wheel for focal-point zoom.
- Toggle the **checkerboard / grid** background to inspect transparent regions.

## Building from source

Requires **JDK 21**. Clone with submodules — the build pulls libpag from `reference/libpag`:

```bash
git clone --recurse-submodules https://github.com/Murmurl912/pag-viewer-plugin.git
cd pag-viewer-plugin
./gradlew buildPlugin
```

The plugin ZIP is written to `build/distributions/pag-viewer-plugin-<version>.zip`.

> **Note:** a local build bundles only the macOS arm64 runtime that ships in the source tree. The complete multi-platform ZIP (macOS arm64 + Linux x86_64 + Windows x86_64) is produced by the GitHub release workflow, which builds or reuses the other runtimes using the helper scripts in [`scripts/`](scripts/). To cut a release, push a `v*` tag:
>
> ```bash
> git tag v0.1.0
> git push origin v0.1.0
> ```

## Publishing

Release signing and JetBrains Marketplace publishing are handled by the GitHub release workflow for `v*` tags. Configure these repository secrets before cutting a Marketplace release:

- `JB_MARKETPLACE_TOKEN` — JetBrains Marketplace personal access token.
- `JB_SIGN_PRIVATE_KEY_B64` — base64-encoded `private.pem`.
- `JB_SIGN_CERTIFICATE_CHAIN_B64` — base64-encoded `chain.crt`.
- `JB_SIGN_PRIVATE_KEY_PASSWORD` — password for the signing private key.

JetBrains requires the first Marketplace upload for a plugin to be done manually. After that initial plugin page exists, the `v*` tag workflow signs the complete native ZIP, uploads the signed ZIP to the GitHub Release, and runs `publishPlugin` for Marketplace publishing. Marketplace publishing is non-blocking in CI: if JetBrains rejects or delays the upload, the GitHub Release still finishes with the signed ZIP attached.

For the first manual Marketplace upload, use the signed ZIP from the GitHub Release assets, for example `pag-viewer-plugin-0.1.0-signed.zip`. Do not upload the GitHub Actions workflow artifact ZIP directly: GitHub wraps workflow artifacts in an extra ZIP, so that wrapper contains the real plugin ZIP as a nested file and Marketplace rejects it. If you downloaded an Actions artifact, unzip it once and upload the inner `pag-viewer-plugin-<version>-signed.zip`.

Run the test suite with `./gradlew test`.

## Limitations & roadmap

- Native runtimes are bundled for macOS arm64, Linux x86_64, and Windows x86_64. Other targets (e.g. macOS Intel, Linux/Windows arm64) are not yet provided.
- JetBrains Marketplace publishing is configured in CI, but the first Marketplace upload still has to be performed manually before automated publishing can upload later versions.
- Not implemented yet: audio playback, editable text/image replacement, layer inspection, timeline markers, PAGViewer-style profiling, and GPU texture rendering.

## Also in this repo

[`tools/web-pag-viewer`](tools/web-pag-viewer) is a small static page that plays PAG files in the browser using the official libpag Web SDK. It is independent of the plugin and exists as a visual reference for comparing playback. Serve it locally with `scripts/serve-web-viewer.sh`.

## Contributing

Issues and pull requests are welcome. Please run `./gradlew test buildPlugin` before opening a PR.

## Acknowledgements

Rendering is powered by [Tencent/libpag](https://github.com/Tencent/libpag), licensed under Apache-2.0.

## License

Released under the [MIT License](LICENSE).
