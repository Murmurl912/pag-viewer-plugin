package com.github.pagviewer.nativebridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class PagNativeLibraryResolverTest {
    @AfterEach
    void clearProperty() {
        System.clearProperty(PagNativeLibraryResolver.LIBRARY_PATH_PROPERTY);
    }

    @Test
    void explicitSystemPropertyWinsOverEnvironmentAndResources() {
        System.setProperty(PagNativeLibraryResolver.LIBRARY_PATH_PROPERTY, "/tmp/libpag-test.dylib");

        PagNativeLibraryResolver resolver = new PagNativeLibraryResolver(
                Map.of(PagNativeLibraryResolver.LIBRARY_PATH_ENV, "/tmp/from-env.dylib"),
                name -> Optional.of(Path.of("/tmp/from-resource.dylib"))
        );

        assertEquals(Path.of("/tmp/libpag-test.dylib"), resolver.resolve().orElseThrow());
    }

    @Test
    void environmentPathIsUsedWhenPropertyIsBlank() {
        System.setProperty(PagNativeLibraryResolver.LIBRARY_PATH_PROPERTY, " ");

        PagNativeLibraryResolver resolver = new PagNativeLibraryResolver(
                Map.of(PagNativeLibraryResolver.LIBRARY_PATH_ENV, "/tmp/from-env.dylib"),
                name -> Optional.empty()
        );

        assertEquals(Path.of("/tmp/from-env.dylib"), resolver.resolve().orElseThrow());
    }

    @Test
    void resourcePathUsesPlatformSpecificLibraryName() {
        PagNativeLibraryResolver resolver = new PagNativeLibraryResolver(
                Map.of(),
                name -> name.contains("native/") && name.endsWith(PagNativeLibraryResolver.mappedLibraryFileName())
                        ? Optional.of(Path.of("/tmp/resource-libpag"))
                        : Optional.empty()
        );

        assertTrue(resolver.resolve().isPresent());
    }

    @Test
    void packagedResourceNamesCoverReleaseRuntimeMatrix() {
        assertEquals(
                List.of("native/macos-aarch64/libpag.dylib"),
                PagNativeLibraryResolver.resourceNames("Mac OS X", "aarch64")
        );
        assertEquals(
                List.of("native/macos-x86_64/libpag.dylib"),
                PagNativeLibraryResolver.resourceNames("Darwin", "x86_64")
        );
        assertEquals(
                List.of("native/linux-x86_64/libpag.so"),
                PagNativeLibraryResolver.resourceNames("Linux", "amd64")
        );
        assertEquals(
                List.of("native/windows-x86_64/pag.dll", "native/windows-x86_64/libpag.dll"),
                PagNativeLibraryResolver.resourceNames("Windows 11", "amd64")
        );
    }

    @Test
    void windowsResolverAcceptsLibpagDllBuildOutputAlias() {
        PagNativeLibraryResolver resolver = new PagNativeLibraryResolver(
                Map.of(),
                name -> name.equals("native/windows-x86_64/libpag.dll")
                        ? Optional.of(Path.of("/tmp/libpag.dll"))
                        : Optional.empty(),
                "Windows 11",
                "amd64"
        );

        assertEquals(Path.of("/tmp/libpag.dll"), resolver.resolve().orElseThrow());
    }

    @Test
    void packagedResourceResolveReusesOneExtractedLibraryPathWithinProcess() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory().equals("macos-aarch64"));

        Path first = new PagNativeLibraryResolver().resolve().orElseThrow();
        Path second = new PagNativeLibraryResolver().resolve().orElseThrow();

        assertEquals(first, second);
    }
}
