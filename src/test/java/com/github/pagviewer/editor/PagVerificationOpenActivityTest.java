package com.github.pagviewer.editor;

import com.intellij.testFramework.LightVirtualFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PagVerificationOpenActivityTest {
    @AfterEach
    void clearProperty() {
        System.clearProperty(PagVerificationOpenActivity.OPEN_FILE_PROPERTY);
    }

    @Test
    void usesSystemPropertyBeforeEnvironment() {
        System.setProperty(PagVerificationOpenActivity.OPEN_FILE_PROPERTY, "/tmp/from-property.pag");

        Optional<Path> path = PagVerificationOpenActivity.configuredFilePath(
                Map.of(PagVerificationOpenActivity.OPEN_FILE_ENV, "/tmp/from-env.pag")
        );

        assertEquals(Path.of("/tmp/from-property.pag"), path.orElseThrow());
    }

    @Test
    void usesEnvironmentWhenPropertyIsBlank() {
        System.setProperty(PagVerificationOpenActivity.OPEN_FILE_PROPERTY, " ");

        Optional<Path> path = PagVerificationOpenActivity.configuredFilePath(
                Map.of(PagVerificationOpenActivity.OPEN_FILE_ENV, "/tmp/from-env.pag")
        );

        assertEquals(Path.of("/tmp/from-env.pag"), path.orElseThrow());
    }

    @Test
    void ignoresBlankAndNonPagValues() {
        assertTrue(PagVerificationOpenActivity.configuredFilePath(Map.of()).isEmpty());
        assertTrue(PagVerificationOpenActivity.configuredFilePath(
                Map.of(PagVerificationOpenActivity.OPEN_FILE_ENV, "/tmp/not-pag.txt")
        ).isEmpty());
    }

    @Test
    void resolvesFileBeforeSchedulingEditorOpenOnUiThread() {
        Path path = Path.of("/tmp/sample.pag");
        LightVirtualFile file = new LightVirtualFile("sample.pag");
        List<String> events = new ArrayList<>();
        List<Runnable> backgroundTasks = new ArrayList<>();
        List<Runnable> uiTasks = new ArrayList<>();

        PagVerificationOpenActivity.scheduleOpenFile(
                path,
                task -> {
                    events.add("background-scheduled");
                    backgroundTasks.add(task);
                },
                task -> {
                    events.add("ui-scheduled");
                    uiTasks.add(task);
                },
                requestedPath -> {
                    events.add("resolve:" + requestedPath);
                    return file;
                },
                openedFile -> events.add("open:" + openedFile.getName())
        );

        assertEquals(List.of("background-scheduled"), events);

        backgroundTasks.get(0).run();
        assertEquals(List.of("background-scheduled", "resolve:/tmp/sample.pag", "ui-scheduled"), events);

        uiTasks.get(0).run();
        assertEquals(List.of(
                "background-scheduled",
                "resolve:/tmp/sample.pag",
                "ui-scheduled",
                "open:sample.pag"
        ), events);
    }

    @Test
    void doesNotScheduleUiOpenWhenFileCannotBeResolved() {
        List<Runnable> backgroundTasks = new ArrayList<>();
        List<Runnable> uiTasks = new ArrayList<>();

        PagVerificationOpenActivity.scheduleOpenFile(
                Path.of("/tmp/missing.pag"),
                backgroundTasks::add,
                uiTasks::add,
                requestedPath -> null,
                openedFile -> {
                    throw new AssertionError("Missing files must not be opened.");
                }
        );

        backgroundTasks.get(0).run();

        assertTrue(uiTasks.isEmpty());
    }
}
