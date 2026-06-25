package com.github.pagviewer.thumbnail

import com.github.pagviewer.nativebridge.JnaPagNativeLibrary
import com.github.pagviewer.nativebridge.PagNativeLibrary
import com.github.pagviewer.nativebridge.PagNativeLibraryResolver

object SharedPagNativeLibrary {
    @Volatile
    private var resolved = false

    @Volatile
    private var library: PagNativeLibrary? = null

    fun get(): PagNativeLibrary? {
        if (resolved) {
            return library
        }
        synchronized(this) {
            if (!resolved) {
                library = try {
                    PagNativeLibraryResolver().resolve().orElse(null)?.let { JnaPagNativeLibrary.load(it) }
                } catch (throwable: Throwable) {
                    null
                }
                resolved = true
            }
            return library
        }
    }
}
