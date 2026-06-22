package com.github.pagviewer.nativebridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
    void packagedResourceResolveReusesOneExtractedLibraryPathWithinProcess() {
        assumeTrue(PagNativeLibraryResolver.platformDirectory().equals("macos-aarch64"));

        Path first = new PagNativeLibraryResolver().resolve().orElseThrow();
        Path second = new PagNativeLibraryResolver().resolve().orElseThrow();

        assertEquals(first, second);
    }
}
