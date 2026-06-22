package com.github.pagviewer.file;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public final class PagFileType implements FileType {
    public static final PagFileType INSTANCE = new PagFileType();

    private PagFileType() {
    }

    @Override
    public @NonNls @NotNull String getName() {
        return "PAG";
    }

    @Override
    public @NotNull String getDescription() {
        return "Portable Animated Graphics animation";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "pag";
    }

    @Override
    public @Nullable Icon getIcon() {
        return AllIcons.FileTypes.Any_type;
    }

    @Override
    public boolean isBinary() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public @Nullable String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
        return null;
    }
}

