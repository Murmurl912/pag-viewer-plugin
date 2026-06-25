package com.github.pagviewer.nativebridge

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.Optional

class PagNativeLibraryResolver internal constructor(
    private val environment: Map<String, String>,
    private val resourceLookup: ResourceLookup,
    private val osName: String?,
    private val archName: String?
) {
    constructor() : this(
        System.getenv(),
        ResourceLookup { resourceName -> extractPackagedLibrary(resourceName) },
        System.getProperty("os.name"),
        System.getProperty("os.arch")
    )

    internal constructor(environment: Map<String, String>, resourceLookup: ResourceLookup) : this(
        environment,
        resourceLookup,
        System.getProperty("os.name"),
        System.getProperty("os.arch")
    )

    fun resolve(): Optional<Path> {
        val propertyPath = pathFrom(System.getProperty(LIBRARY_PATH_PROPERTY))
        if (propertyPath.isPresent) {
            return propertyPath
        }

        val environmentPath = pathFrom(environment[LIBRARY_PATH_ENV])
        if (environmentPath.isPresent) {
            return environmentPath
        }

        for (resourceName in resourceNames(osName, archName)) {
            val resourcePath = resourceLookup.find(resourceName)
            if (resourcePath.isPresent) {
                return resourcePath
            }
        }
        return Optional.empty()
    }

    fun interface ResourceLookup {
        fun find(resourceName: String): Optional<Path>
    }

    companion object {
        const val LIBRARY_PATH_PROPERTY = "pag.viewer.libpag.path"
        const val LIBRARY_PATH_ENV = "PAG_VIEWER_LIBPAG_PATH"

        private val PACKAGED_LIBRARY_LOCK = Any()
        private val EXTRACTED_PACKAGED_LIBRARIES = HashMap<String, Path>()

        fun mappedLibraryFileName(): String = mappedLibraryFileName(System.getProperty("os.name"))

        fun platformDirectory(): String =
            platformDirectory(System.getProperty("os.name"), System.getProperty("os.arch"))

        fun resourceNames(osName: String?, archName: String?): List<String> {
            val directory = platformDirectory(osName, archName)
            val mappedName = mappedLibraryFileName(osName)
            if (normalizeOs(osName) == "windows") {
                return listOf(
                    "native/$directory/$mappedName",
                    "native/$directory/libpag.dll"
                )
            }
            return listOf("native/$directory/$mappedName")
        }

        private fun mappedLibraryFileName(osName: String?): String = when (normalizeOs(osName)) {
            "macos" -> "libpag.dylib"
            "windows" -> "pag.dll"
            else -> "libpag.so"
        }

        private fun platformDirectory(osName: String?, archName: String?): String =
            normalizeOs(osName) + "-" + normalizeArch(archName)

        private fun pathFrom(value: String?): Optional<Path> {
            if (value == null || value.isBlank()) {
                return Optional.empty()
            }
            return Optional.of(Path.of(value.trim()))
        }

        private fun extractPackagedLibrary(resourceName: String): Optional<Path> {
            synchronized(PACKAGED_LIBRARY_LOCK) {
                val cached = EXTRACTED_PACKAGED_LIBRARIES[resourceName]
                if (cached != null && Files.isRegularFile(cached)) {
                    return Optional.of(cached)
                }

                val extracted = extractPackagedLibraryOnce(resourceName)
                extracted.ifPresent { path -> EXTRACTED_PACKAGED_LIBRARIES[resourceName] = path }
                return extracted
            }
        }

        private fun extractPackagedLibraryOnce(resourceName: String): Optional<Path> {
            val loader = PagNativeLibraryResolver::class.java.classLoader
            try {
                loader.getResourceAsStream(resourceName).use { stream ->
                    if (stream == null) {
                        return Optional.empty()
                    }
                    val target = Files.createTempFile("pag-viewer-", "-" + Path.of(resourceName).fileName)
                    Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
                    target.toFile().setExecutable(true, true)
                    target.toFile().deleteOnExit()
                    return Optional.of(target)
                }
            } catch (ignored: IOException) {
                return Optional.empty()
            }
        }

        private fun normalizeOs(osName: String?): String {
            val name = osName?.lowercase(Locale.ROOT) ?: ""
            if (name.contains("mac") || name.contains("darwin")) {
                return "macos"
            }
            if (name.contains("win")) {
                return "windows"
            }
            return "linux"
        }

        private fun normalizeArch(archName: String?): String {
            val arch = archName?.lowercase(Locale.ROOT) ?: ""
            if (arch == "aarch64" || arch == "arm64") {
                return "aarch64"
            }
            return "x86_64"
        }
    }
}
