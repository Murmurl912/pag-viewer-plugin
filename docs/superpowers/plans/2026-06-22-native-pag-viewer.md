# Native PAG Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a first native-backed IntelliJ plugin that previews `.pag` animations without JCEF.

**Architecture:** Register `.pag` as a binary file type and open it with a custom Swing `FileEditor`. The editor reads file bytes, loads libpag through a small JNA wrapper around the public C ABI, decodes CPU frames, and paints them as `BufferedImage` frames with playback controls.

**Tech Stack:** Java 21, IntelliJ Platform Gradle Plugin 2.16.0, IntelliJ Platform 2026.1.3, JNA 5.17.0, JUnit 5.13.1, Tencent/libpag C API.

---

### Task 1: Build and Test Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `src/main/resources/META-INF/plugin.xml`
- Create: `docs/TASKS.md`
- Create: `docs/PORTING_MEMO.md`

- [x] **Step 1: Add Gradle and plugin metadata**

Use IntelliJ Platform Gradle Plugin 2.16.0 and target IntelliJ IDEA Community 2026.1.3.

- [ ] **Step 2: Generate Gradle wrapper**

Run: `/tmp/gradle-9.0.0/bin/gradle wrapper --gradle-version 9.0.0`

Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/*` are created.

### Task 2: Native Preview Core

**Files:**
- Test: `src/test/java/com/github/pagviewer/nativebridge/PagFrameClockTest.java`
- Test: `src/test/java/com/github/pagviewer/nativebridge/PagNativeLibraryResolverTest.java`
- Test: `src/test/java/com/github/pagviewer/nativebridge/PagPreviewSessionTest.java`
- Create: `src/main/java/com/github/pagviewer/nativebridge/PagFrameClock.java`
- Create: `src/main/java/com/github/pagviewer/nativebridge/PagNativeLibraryResolver.java`
- Create: `src/main/java/com/github/pagviewer/nativebridge/PagNativeLibrary.java`
- Create: `src/main/java/com/github/pagviewer/nativebridge/JnaPagNativeLibrary.java`
- Create: `src/main/java/com/github/pagviewer/nativebridge/PagPreviewSession.java`
- Create: `src/main/java/com/github/pagviewer/nativebridge/PagPreviewInfo.java`

- [x] **Step 1: Write failing tests**

Tests define frame timing, library resolution, and BGRA frame conversion.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew test --tests 'com.github.pagviewer.nativebridge.*'`

Expected: FAIL because native bridge classes are not implemented yet.

- [ ] **Step 3: Implement minimal native preview core**

Implement only APIs required by the tests and editor playback.

### Task 3: IntelliJ Editor UI

**Files:**
- Create: `src/main/java/com/github/pagviewer/file/PagFileType.java`
- Create: `src/main/java/com/github/pagviewer/editor/PagFileEditorProvider.java`
- Create: `src/main/java/com/github/pagviewer/editor/PagFileEditor.java`
- Create: `src/main/java/com/github/pagviewer/editor/PagViewerPanel.java`
- Create: `src/main/java/com/github/pagviewer/editor/PagCanvas.java`

- [ ] **Step 1: Register `.pag` file type**

Create a binary `FileType` singleton with `.pag` extension registration in `plugin.xml`.

- [ ] **Step 2: Register `FileEditorProvider`**

Accept only `.pag` files and return the Swing editor.

- [ ] **Step 3: Build playback panel**

Load `PagPreviewSession`, show status, render frames, and expose play/pause and frame slider.

### Task 4: Native Artifact Verification

**Files:**
- Modify: `docs/PORTING_MEMO.md`
- Optional create: `src/main/resources/native/<platform>/<libpag dynamic library>`

- [ ] **Step 1: Try local libpag dynamic build**

Run CMake from `reference/libpag` with tests disabled and shared library enabled.

- [ ] **Step 2: Package or document native library path**

If build succeeds, copy the dynamic library into platform resources. If not, document the exact blocker and use `PAG_VIEWER_LIBPAG_PATH` as the human-verification path.

### Task 5: Final Verification Gate

**Files:**
- Modify: `docs/TASKS.md`
- Modify: `docs/PORTING_MEMO.md`

- [ ] **Step 1: Run unit tests**

Run: `./gradlew test`

Expected: all tests pass.

- [ ] **Step 2: Run plugin build**

Run: `./gradlew buildPlugin`

Expected: plugin ZIP is produced in `build/distributions`.

- [ ] **Step 3: Stop for human verification**

Ask the user to open a real `.pag` file in IntelliJ IDEA or Android Studio and confirm preview playback.

