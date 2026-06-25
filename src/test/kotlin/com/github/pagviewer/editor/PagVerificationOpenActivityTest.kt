package com.github.pagviewer.editor

import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class PagVerificationOpenActivityTest {
    @AfterEach
    fun clearProperty() {
        System.clearProperty(PagVerificationOpenActivity.OPEN_FILE_PROPERTY)
    }

    @Test
    fun usesSystemPropertyBeforeEnvironment() {
        System.setProperty(PagVerificationOpenActivity.OPEN_FILE_PROPERTY, "/tmp/from-property.pag")

        val path = PagVerificationOpenActivity.configuredFilePath(
            mapOf(PagVerificationOpenActivity.OPEN_FILE_ENV to "/tmp/from-env.pag")
        )

        assertEquals(Path.of("/tmp/from-property.pag"), path.orElseThrow())
    }

    @Test
    fun usesEnvironmentWhenPropertyIsBlank() {
        System.setProperty(PagVerificationOpenActivity.OPEN_FILE_PROPERTY, " ")

        val path = PagVerificationOpenActivity.configuredFilePath(
            mapOf(PagVerificationOpenActivity.OPEN_FILE_ENV to "/tmp/from-env.pag")
        )

        assertEquals(Path.of("/tmp/from-env.pag"), path.orElseThrow())
    }

    @Test
    fun ignoresBlankAndNonPagValues() {
        assertTrue(PagVerificationOpenActivity.configuredFilePath(emptyMap()).isEmpty)
        assertTrue(
            PagVerificationOpenActivity.configuredFilePath(
                mapOf(PagVerificationOpenActivity.OPEN_FILE_ENV to "/tmp/not-pag.txt")
            ).isEmpty
        )
    }

    @Test
    fun resolvesFileBeforeSchedulingEditorOpenOnUiThread() {
        val path = Path.of("/tmp/sample.pag")
        val file = LightVirtualFile("sample.pag")
        val events = mutableListOf<String>()
        val backgroundTasks = mutableListOf<Runnable>()
        val uiTasks = mutableListOf<Runnable>()

        PagVerificationOpenActivity.scheduleOpenFile(
            path,
            { task ->
                events.add("background-scheduled")
                backgroundTasks.add(task)
            },
            { task ->
                events.add("ui-scheduled")
                uiTasks.add(task)
            },
            { requestedPath ->
                events.add("resolve:$requestedPath")
                file
            },
            { openedFile -> events.add("open:${openedFile.name}") }
        )

        assertEquals(listOf("background-scheduled"), events)

        backgroundTasks[0].run()
        assertEquals(listOf("background-scheduled", "resolve:/tmp/sample.pag", "ui-scheduled"), events)

        uiTasks[0].run()
        assertEquals(
            listOf(
                "background-scheduled",
                "resolve:/tmp/sample.pag",
                "ui-scheduled",
                "open:sample.pag"
            ),
            events
        )
    }

    @Test
    fun doesNotScheduleUiOpenWhenFileCannotBeResolved() {
        val backgroundTasks = mutableListOf<Runnable>()
        val uiTasks = mutableListOf<Runnable>()

        PagVerificationOpenActivity.scheduleOpenFile(
            Path.of("/tmp/missing.pag"),
            { task -> backgroundTasks.add(task) },
            { task -> uiTasks.add(task) },
            { null },
            { throw AssertionError("Missing files must not be opened.") }
        )

        backgroundTasks[0].run()

        assertTrue(uiTasks.isEmpty())
    }
}
