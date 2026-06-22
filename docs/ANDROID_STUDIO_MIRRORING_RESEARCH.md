# Android Studio Device Mirroring Research

Date: 2026-06-22

## Question

Can the PAG viewer borrow the Android Studio device mirroring implementation to avoid slow Swing image playback?

## Local Android Studio Findings

Installed Android Studio contains the mirroring stack in the Android plugin:

- IDE classes: `/Applications/Android Studio.app/Contents/plugins/android/lib/android.jar`
- Device-side agent: `/Applications/Android Studio.app/Contents/plugins/android/resources/screen-sharing-agent/screen-sharing-agent.jar`
- Device-side native agent libraries:
  - `/Applications/Android Studio.app/Contents/plugins/android/resources/screen-sharing-agent/arm64-v8a/libscreen-sharing-agent.so`
  - `/Applications/Android Studio.app/Contents/plugins/android/resources/screen-sharing-agent/x86_64/libscreen-sharing-agent.so`
  - plus `armeabi-v7a` and `x86`
- Native video dependencies:
  - `/Applications/Android Studio.app/Contents/plugins/android/lib/ffmpeg-6.0-1.5.9.jar`
  - `/Applications/Android Studio.app/Contents/plugins/android/lib/ffmpeg-platform-6.0-1.5.9.jar`
  - `/Applications/Android Studio.app/Contents/plugins/android/lib/javacpp-1.5.9.jar`

Relevant IDE classes found in `android.jar`:

- `com.android.tools.idea.streaming.device.DeviceView`
- `com.android.tools.idea.streaming.device.VideoDecoder`
- `com.android.tools.idea.streaming.device.VideoDecoder.VideoFrame`
- `com.android.tools.idea.streaming.core.AbstractDisplayView`
- `com.android.tools.idea.streaming.core.AbstractDisplayPanel`
- `com.android.tools.idea.streaming.core.VolatileImageBufferingPainter`

## Upstream Source References

- [VideoDecoder.kt](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/streaming/src/com/android/tools/idea/streaming/device/VideoDecoder.kt)
- [DeviceView.kt](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/streaming/src/com/android/tools/idea/streaming/device/DeviceView.kt)
- [VolatileImageBufferingPainter.kt](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/streaming/src/com/android/tools/idea/streaming/core/VolatileImageBufferingPainter.kt)
- [streaming/BUILD](https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/mirror-goog-studio-main/streaming/BUILD)

## How Android Studio Mirroring Works

The device mirroring stack is not a reusable texture widget. It is a full streaming pipeline:

1. Android Studio deploys/uses a device-side screen-sharing agent.
2. The IDE receives encoded video packets over streaming channels.
3. `VideoDecoder` uses JavaCPP/FFmpeg (`AVCodec`, `AVFrame`, `sws_scale`) to decode and convert frames.
4. Decoded frames are represented as `BufferedImage` inside `VideoDecoder.VideoFrame`.
5. `DeviceView.paintComponent()` calls `VideoDecoder.consumeDisplayFrame(...)`, then paints the latest `BufferedImage` with `Graphics2D.drawImage(...)`.
6. The view delays high-quality scaling for downscaled frames: it first paints quickly with an affine transform, then schedules a higher-quality repaint after the stream has been stable for a short time.
7. The shared streaming UI includes `VolatileImageBufferingPainter`, which uses a `VolatileImage` as an intermediate buffer that can sometimes be hardware accelerated and handles HiDPI alignment carefully.

This means Android Studio is still image-backed at the final Swing view layer, but it avoids many costs by treating the input as a video stream:

- decoder work is off the EDT,
- images are reused when size/orientation allow it,
- decoded pixels are written through `DataBufferInt`,
- painting is scheduled from frame availability,
- expensive quality scaling is delayed,
- optional `VolatileImage` buffering can reduce display-surface churn.

## Can We Borrow It?

Direct dependency: **not recommended**.

- The classes are internal Android Studio implementation details, not IntelliJ Platform API.
- They are packaged in Android Studio's Android plugin and are absent in plain IntelliJ IDEA.
- Depending on them would break the goal of supporting IntelliJ IDEA, Android Studio, and other JetBrains IDEs with one plugin.
- Their constructors depend on Android-specific device clients, settings, input managers, and tool window infrastructure.

Conceptual/code adaptation: **yes**.

Pieces we can safely borrow as design patterns:

- Add a local `VolatileImage` buffering painter adapted from `VolatileImageBufferingPainter`.
- Change `PagCanvas` to paint through that volatile buffer for the composed background + PAG frame + grid.
- Add the Android Studio high-quality delayed repaint pattern: fast transform during playback, higher-quality scaling after playback/scrub settles.
- Keep the decoder off the EDT and notify repaint only when a new decoded frame is available.
- Reuse Android Studio's frame handoff model: a synchronized latest-frame container consumed by paint, rather than paint asking the decoder synchronously.

Pieces we should not borrow for PAG playback:

- The screen-sharing agent and protocol. PAG is local file rendering, not a remote device stream.
- JavaCPP/FFmpeg. libpag is already the decoder/renderer; adding FFmpeg does not help PAG rendering.
- Android device input/control stack.

## Implication For The PAG Viewer

There are two realistic implementation tracks:

1. **Near-term Swing acceleration pass: implemented**
   - Add a local Java `VolatileImageBufferingPainter`.
   - Let `PagCanvas` render checkerboard, current frame, and grid into the volatile buffer.
   - Use fast transform while playing and delayed high-quality repaint when idle.
   - This borrows Android Studio's display strategy and should reduce Swing paint overhead, but it still uses CPU decoded frames.

2. **Real GPU renderer**
   - Build a separate `PagGpuCanvas` using libpag `PAGPlayer` + `PAGSurface`.
   - Use `pag_surface_make_from_texture(...)` with an OpenGL texture/render target, or a macOS Metal native bridge later.
   - Keep the current CPU/Swing renderer as fallback.
   - This is the path that can avoid CPU frame extraction entirely.

Recommended sequence: keep the implemented volatile-image/high-quality-repaint pass as the default renderer, then prototype `PagGpuCanvas` behind a feature flag.
