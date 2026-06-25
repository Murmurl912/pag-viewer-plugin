plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform")
}

group = "com.github.pagviewer"
version = providers.gradleProperty("pluginVersion").get()

val requestedRemotePlatformProvider = providers.gradleProperty("useRemotePlatform")
    .map { it.toBoolean() }
    .orElse(providers.environmentVariable("USE_REMOTE_PLATFORM").map { it.toBoolean() })
    .orElse(false)

val platformLocalPathProvider = providers.gradleProperty("platformLocalPath")
    .orElse(providers.environmentVariable("PLATFORM_LOCAL_PATH"))

val autoDetectedPlatformPathProvider = providers.provider {
    listOf(
        "/Applications/Android Studio.app",
        "/Applications/IntelliJ IDEA.app",
        "${System.getProperty("user.home")}/Applications/IntelliJ IDEA.app"
    ).firstOrNull { file(it).isDirectory }
}

val remotePlatformVersionProvider = providers.gradleProperty("platformVersion")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("net.java.dev.jna:jna:5.17.0")

    testImplementation(platform("org.junit:junit-bom:5.13.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("junit:junit:4.13.2")

    intellijPlatform {
        val requestedRemotePlatform = requestedRemotePlatformProvider.get()
        val configuredLocalPath = platformLocalPathProvider.orNull
        val detectedLocalPath = autoDetectedPlatformPathProvider.orNull
        when {
            requestedRemotePlatform -> intellijIdeaCommunity(remotePlatformVersionProvider.get())
            !configuredLocalPath.isNullOrBlank() -> local(configuredLocalPath)
            !detectedLocalPath.isNullOrBlank() -> local(detectedLocalPath)
            else -> intellijIdeaCommunity(remotePlatformVersionProvider.get())
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
    }
}

intellijPlatform {
    signing {
        privateKeyFile.set(
            providers.gradleProperty("intellijPlatformSigningPrivateKeyFile")
                .map { layout.projectDirectory.file(it) }
        )
        certificateChainFile.set(
            providers.gradleProperty("intellijPlatformSigningCertificateChainFile")
                .map { layout.projectDirectory.file(it) }
        )
        password.set(providers.gradleProperty("intellijPlatformSigningPassword"))
    }

    publishing {
        token.set(providers.gradleProperty("intellijPlatformPublishingToken"))
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("252")
        untilBuild.set(provider { null })
    }

    buildSearchableOptions {
        enabled = false
    }

    named<JavaExec>("runIde") {
        providers.gradleProperty("pagViewerOpenOnStartup").orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { jvmArgs("-Dpag.viewer.open.on.startup=${it.trim()}") }
    }
}
