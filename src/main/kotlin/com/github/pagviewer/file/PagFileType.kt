package com.github.pagviewer.file

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

object PagFileType : FileType {
    override fun getName(): String = "PAG"

    override fun getDescription(): String = "Portable Animated Graphics animation"

    override fun getDefaultExtension(): String = "pag"

    override fun getIcon(): Icon? = AllIcons.FileTypes.Any_type

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
