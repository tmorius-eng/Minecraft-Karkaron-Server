// Root build for SULD — Mongolian Hardcore MMORPG.
//
// Shared configuration for every module. Per-module specifics (Paper API,
// shading, etc.) live in the module build scripts.

plugins {
    java
}

allprojects {
    group = "mn.suld"
    version = "0.1.0-SNAPSHOT"

    repositories {
        // Google's byte-identical read-only mirror of Maven Central. Listed
        // first ONLY to survive this build's Central rate-limiting (HTTP 429);
        // it serves identical Central bytes, not third-party artifacts. In an
        // unrated network, mavenCentral() alone suffices and this can be
        // removed. See DEPENDENCIES.md. (Paper repo is declared in :suld-plugin.)
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "mavenCentralMirror"
        }
        // Authoritative Maven Central (preferred source of record).
        mavenCentral()
    }
}

// Java toolchain is configurable so the build can track Paper's required JDK.
// Current supported Paper (1.21.x) requires Java 21; bump `javaVersion` to 25
// (or newer) once a matching JDK/toolchain is available in your environment.
val javaVersion = (findProperty("javaVersion") as String? ?: "21").toInt()

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
