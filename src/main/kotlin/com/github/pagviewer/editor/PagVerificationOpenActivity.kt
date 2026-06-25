package com.github.pagviewer.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.util.Locale
import java.util.Optional

class PagVerificationOpenActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        configuredFilePath(System.getenv()).ifPresent { path ->
            scheduleOpenFile(
                path,
                { task -> ApplicationManager.getApplication().executeOnPooledThread(task) },
                { task -> ApplicationManager.getApplication().invokeLater(task, project.disposed) },
                { requestedPath -> LocalFileSystem.getInstance().refreshAndFindFileByNioFile(requestedPath) },
                { file -> openFile(project, file) }
            )
        }
    }

    companion object {
        const val OPEN_FILE_PROPERTY = "pag.viewer.open.on.startup"
        const val OPEN_FILE_ENV = "PAG_VIEWER_OPEN_ON_STARTUP"

        private val LOG = Logger.getInstance(PagVerificationOpenActivity::class.java)

        fun configuredFilePath(environment: Map<String, String>): Optional<Path> =
            pathFrom(System.getProperty(OPEN_FILE_PROPERTY))
                .or { pathFrom(environment[OPEN_FILE_ENV]) }
                .filter { isPagPath(it) }

        fun scheduleOpenFile(
            path: Path,
            runInBackground: (Runnable) -> Unit,
            runOnUiThread: (Runnable) -> Unit,
            resolveFile: (Path) -> VirtualFile?,
            openEditor: (VirtualFile) -> Unit
        ) {
            LOG.info("PAG verification file open requested: $path")
            runInBackground(Runnable {
                val file = resolveFile(path)
                if (file == null) {
                    LOG.warn("PAG verification file is not visible to the local file system: $path")
                    return@Runnable
                }
                runOnUiThread(Runnable { openEditor(file) })
            })
        }

        private fun pathFrom(value: String?): Optional<Path> {
            if (value == null || value.isBlank()) {
                return Optional.empty()
            }
            return Optional.of(Path.of(value.trim()).toAbsolutePath())
        }

        private fun isPagPath(path: Path): Boolean {
            val name = path.fileName?.toString()?.lowercase(Locale.ROOT) ?: ""
            return name.endsWith(".pag")
        }

        private fun openFile(project: Project, file: VirtualFile) {
            val editors = FileEditorManager.getInstance(project).openFile(file, true)
            LOG.info("PAG verification file open completed: ${file.path}, editors=${editors.size}")
        }
    }
}
