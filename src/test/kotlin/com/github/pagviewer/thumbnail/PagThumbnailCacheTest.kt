package com.github.pagviewer.thumbnail

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.Icon

internal class PagThumbnailCacheTest {
    @AfterEach
    fun tearDown() {
        PagThumbnailCache.clear()
    }

    @Test
    fun keyChangesWithVersion() {
        val base = PagThumbnailCache.cacheKey("file:///a.pag", 1L, 10L)
        assertNotEquals(base, PagThumbnailCache.cacheKey("file:///a.pag", 2L, 10L))
        assertNotEquals(base, PagThumbnailCache.cacheKey("file:///a.pag", 1L, 11L))
    }

    @Test
    fun storesNullAsNoThumbnailSentinel() {
        PagThumbnailCache.put("k", null)
        assertSame(PagThumbnailCache.noThumbnail, PagThumbnailCache.get("k"))
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(PagThumbnailCache.get("absent"))
    }

    @Test
    fun evictsOldestBeyondCapacity() {
        val icon: Icon = PagThumbnailCache.noThumbnail
        repeat(300) { PagThumbnailCache.put("key-$it", icon) }
        assertTrue(PagThumbnailCache.size() <= 256)
        assertNull(PagThumbnailCache.get("key-0"))
        assertSame(icon, PagThumbnailCache.get("key-299"))
    }
}
