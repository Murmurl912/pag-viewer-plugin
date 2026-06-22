package com.github.pagviewer.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class PagVerificationOpenActivity implements StartupActivity.DumbAware {
    static final String OPEN_FILE_PROPERTY = "pag.viewer.open.on.startup";
    static final String OPEN_FILE_ENV = "PAG_VIEWER_OPEN_ON_STARTUP";

    private static final Logger LOG = Logger.getInstance(PagVerificationOpenActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        configuredFilePath(System.getenv()).ifPresent(path -> scheduleOpenFile(
                path,
                task -> ApplicationManager.getApplication().executeOnPooledThread(task),
                task -> ApplicationManager.getApplication().invokeLater(task, project.getDisposed()),
                requestedPath -> LocalFileSystem.getInstance().refreshAndFindFileByNioFile(requestedPath),
                file -> openFile(project, file)
        ));
    }

    static Optional<Path> configuredFilePath(Map<String, String> environment) {
        return pathFrom(System.getProperty(OPEN_FILE_PROPERTY))
                .or(() -> pathFrom(environment.get(OPEN_FILE_ENV)))
                .filter(PagVerificationOpenActivity::isPagPath);
    }

    private static Optional<Path> pathFrom(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(value.trim()).toAbsolutePath());
    }

    private static boolean isPagPath(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pag");
    }

    static void scheduleOpenFile(
            @NotNull Path path,
            @NotNull Consumer<Runnable> runInBackground,
            @NotNull Consumer<Runnable> runOnUiThread,
            @NotNull Function<Path, VirtualFile> resolveFile,
            @NotNull Consumer<VirtualFile> openEditor
    ) {
        LOG.info("PAG verification file open requested: " + path);
        runInBackground.accept(() -> {
            VirtualFile file = resolveFile.apply(path);
            if (file == null) {
                LOG.warn("PAG verification file is not visible to the local file system: " + path);
                return;
            }

            runOnUiThread.accept(() -> openEditor.accept(file));
        });
    }

    private static void openFile(Project project, VirtualFile file) {
        FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
        LOG.info("PAG verification file open completed: " + file.getPath() + ", editors=" + editors.length);
    }
}
