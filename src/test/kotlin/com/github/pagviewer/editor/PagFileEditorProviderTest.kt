package com.github.pagviewer.editor

import com.github.pagviewer.file.PagFileType
import com.intellij.mock.MockProject
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PagFileEditorProviderTest {
    @Test
    fun pagFileTypeDescribesBinaryPagFiles() {
        assertEquals("PAG", PagFileType.getName())
        assertEquals("pag", PagFileType.getDefaultExtension())
        assertTrue(PagFileType.isBinary())
    }

    @Test
    fun providerAcceptsOnlyPagExtension() {
        val disposable = Disposer.newDisposable()
        val project = MockProject(null, disposable)
        val provider = PagFileEditorProvider()

        try {
            assertTrue(provider.accept(project, LightVirtualFile("animation.pag")))
            assertTrue(provider.accept(project, LightVirtualFile("ANIMATION.PAG")))
            assertFalse(provider.accept(project, LightVirtualFile("animation.json")))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    @Test
    fun providerIsDumbAwareBecauseItHidesTheDefaultEditor() {
        val provider: Any = PagFileEditorProvider()

        assertTrue(provider is DumbAware)
    }
}
