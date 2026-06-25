package com.github.pagviewer.nativebridge

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Optional

internal class PagNativeLibraryResolverTest {
    @AfterEach
    fun clearProperty() {
        System.clearProperty(PagNativeLibraryResolver.LIBRARY_PATH_PROPERTY)
    }

    @Test
    fun explicitSystemPropertyWinsOverEnvironmentAndResources() {
        System.setProperty(PagNativeLibraryResolver.LIBRARY_PATH_PROPERTY, "/tmp/libpag-test.dylib")

        val resolver = PagNativeLibraryResolver(
            mapOf(PagNativeLibraryResolver.LIBRARY_PATH_ENV to "/tmp/from-env.dylib"),
            { Optional.of(Path.of("/tmp/from-resource.dylib")) }
        )

        assertEquals(Path.of("/tmp/libpag-test.dylib"), resolver.resolve().orElseThrow())
    }

    @Test
    fun environmentPathIsUsedWhenPropertyIsBlank() {
        System.setProperty(PagNativeLibraryResolver.LIBRARY_PATH_PROPERTY, " ")

        val resolver = PagNativeLibraryResolver(
            mapOf(PagNativeLibraryResolver.LIBRARY_PATH_ENV to "/tmp/from-env.dylib"),
            { Optional.empty<Path>() }
        )

        assertEquals(Path.of("/tmp/from-env.dylib"), resolver.resolve().orElseThrow())
    }

    @Test
    fun resourcePathUsesPlatformSpecificLibraryName() {
        val resolver = PagNativeLibraryResolver(
            emptyMap(),
            { name ->
                if (name.contains("native/") && name.endsWith(PagNativeLibraryResolver.mappedLibraryFileName())) {
                    Optional.of(Path.of("/tmp/resource-libpag"))
                } else {
                    Optional.empty()
                }
            }
        )

        assertTrue(resolver.resolve().isPresent)
    }

    @Test
    fun packagedResourceNamesCoverReleaseRuntimeMatrix() {
        assertEquals(
            listOf("native/macos-aarch64/libpag.dylib"),
            PagNativeLibraryResolver.resourceNames("Mac OS X", "aarch64")
        )
        assertEquals(
            listOf("native/macos-x86_64/libpag.dylib"),
            PagNativeLibraryResolver.resourceNames("Darwin", "x86_64")
        )
        assertEquals(
            listOf("native/linux-x86_64/libpag.so"),
            PagNativeLibraryResolver.resourceNames("Linux", "amd64")
        )
        assertEquals(
            listOf("native/windows-x86_64/pag.dll", "native/windows-x86_64/libpag.dll"),
            PagNativeLibraryResolver.resourceNames("Windows 11", "amd64")
        )
    }

    @Test
    fun windowsResolverAcceptsLibpagDllBuildOutputAlias() {
        val resolver = PagNativeLibraryResolver(
            emptyMap(),
            { name ->
                if (name == "native/windows-x86_64/libpag.dll") {
                    Optional.of(Path.of("/tmp/libpag.dll"))
                } else {
                    Optional.empty()
                }
            },
            "Windows 11",
            "amd64"
        )

        assertEquals(Path.of("/tmp/libpag.dll"), resolver.resolve().orElseThrow())
    }

    @Test
    fun packagedResourceResolveReusesOneExtractedLibraryPathWithinProcess() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory() == "macos-aarch64")

        val first = PagNativeLibraryResolver().resolve().orElseThrow()
        val second = PagNativeLibraryResolver().resolve().orElseThrow()

        assertEquals(first, second)
    }
}
