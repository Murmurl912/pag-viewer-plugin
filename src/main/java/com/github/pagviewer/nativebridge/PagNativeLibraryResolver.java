package com.github.pagviewer.nativebridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PagNativeLibraryResolver {
    public static final String LIBRARY_PATH_PROPERTY = "pag.viewer.libpag.path";
    public static final String LIBRARY_PATH_ENV = "PAG_VIEWER_LIBPAG_PATH";
    private static final Object PACKAGED_LIBRARY_LOCK = new Object();
    private static final Map<String, Path> EXTRACTED_PACKAGED_LIBRARIES = new HashMap<>();

    private final Map<String, String> environment;
    private final ResourceLookup resourceLookup;

    public PagNativeLibraryResolver() {
        this(System.getenv(), PagNativeLibraryResolver::extractPackagedLibrary);
    }

    PagNativeLibraryResolver(Map<String, String> environment, ResourceLookup resourceLookup) {
        this.environment = environment;
        this.resourceLookup = resourceLookup;
    }

    public Optional<Path> resolve() {
        Optional<Path> propertyPath = pathFrom(System.getProperty(LIBRARY_PATH_PROPERTY));
        if (propertyPath.isPresent()) {
            return propertyPath;
        }

        Optional<Path> environmentPath = pathFrom(environment.get(LIBRARY_PATH_ENV));
        if (environmentPath.isPresent()) {
            return environmentPath;
        }

        return resourceLookup.find(resourceName());
    }

    public static String mappedLibraryFileName() {
        return System.mapLibraryName("pag");
    }

    public static String platformDirectory() {
        return normalizeOs(System.getProperty("os.name")) + "-" + normalizeArch(System.getProperty("os.arch"));
    }

    private static String resourceName() {
        return "native/" + platformDirectory() + "/" + mappedLibraryFileName();
    }

    private static Optional<Path> pathFrom(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(value.trim()));
    }

    private static Optional<Path> extractPackagedLibrary(String resourceName) {
        synchronized (PACKAGED_LIBRARY_LOCK) {
            Path cached = EXTRACTED_PACKAGED_LIBRARIES.get(resourceName);
            if (cached != null && Files.isRegularFile(cached)) {
                return Optional.of(cached);
            }

            Optional<Path> extracted = extractPackagedLibraryOnce(resourceName);
            extracted.ifPresent(path -> EXTRACTED_PACKAGED_LIBRARIES.put(resourceName, path));
            return extracted;
        }
    }

    private static Optional<Path> extractPackagedLibraryOnce(String resourceName) {
        ClassLoader loader = PagNativeLibraryResolver.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                return Optional.empty();
            }
            Path target = Files.createTempFile("pag-viewer-", "-" + Path.of(resourceName).getFileName());
            Files.copy(stream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            target.toFile().setExecutable(true, true);
            target.toFile().deleteOnExit();
            return Optional.of(target);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static String normalizeOs(String osName) {
        String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (name.contains("mac") || name.contains("darwin")) {
            return "macos";
        }
        if (name.contains("win")) {
            return "windows";
        }
        return "linux";
    }

    private static String normalizeArch(String archName) {
        String arch = archName == null ? "" : archName.toLowerCase(Locale.ROOT);
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        return "x86_64";
    }

    @FunctionalInterface
    interface ResourceLookup {
        Optional<Path> find(String resourceName);
    }
}
