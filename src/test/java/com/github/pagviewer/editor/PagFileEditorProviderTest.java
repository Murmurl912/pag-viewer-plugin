package com.github.pagviewer.editor;

import com.github.pagviewer.file.PagFileType;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightVirtualFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PagFileEditorProviderTest {
    @Test
    void pagFileTypeDescribesBinaryPagFiles() {
        assertEquals("PAG", PagFileType.INSTANCE.getName());
        assertEquals("pag", PagFileType.INSTANCE.getDefaultExtension());
        assertTrue(PagFileType.INSTANCE.isBinary());
    }

    @Test
    void providerAcceptsOnlyPagExtension() {
        Disposable disposable = Disposer.newDisposable();
        MockProject project = new MockProject(null, disposable);
        PagFileEditorProvider provider = new PagFileEditorProvider();

        try {
            assertTrue(provider.accept(project, new LightVirtualFile("animation.pag")));
            assertTrue(provider.accept(project, new LightVirtualFile("ANIMATION.PAG")));
            assertFalse(provider.accept(project, new LightVirtualFile("animation.json")));
        } finally {
            Disposer.dispose(disposable);
        }
    }

    @Test
    void providerIsDumbAwareBecauseItHidesTheDefaultEditor() {
        Object provider = new PagFileEditorProvider();

        assertTrue(provider instanceof DumbAware);
    }
}
