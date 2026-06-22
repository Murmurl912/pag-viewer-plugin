plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.github.pagviewer"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("net.java.dev.jna:jna:5.17.0")

    testImplementation(platform("org.junit:junit-bom:5.13.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("junit:junit:4.13.2")

    intellijPlatform {
        local(providers.gradleProperty("platformLocalPath").get())
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
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
