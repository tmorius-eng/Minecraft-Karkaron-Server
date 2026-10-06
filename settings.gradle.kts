pluginManagement {
    repositories {
        // Google's byte-identical read-only mirror of Maven Central, listed
        // first ONLY because this build's egress IP is rate-limited by the
        // canonical Central endpoint (HTTP 429) and the Plugin Portal redirects
        // library JAR downloads to Central, which Gradle cannot fall back from
        // mid-module. The mirror serves identical Central bytes (not a
        // third-party repo). In an unrated network, mavenCentral() alone works
        // and this line can be removed. See DEPENDENCIES.md for the full audit.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "mavenCentralMirror"
        }
        // Authoritative Maven Central (preferred source of record).
        mavenCentral()
        // Home of Gradle plugin marker artifacts (Shadow, run-paper) that exist
        // on no other repository. Last so ordinary libraries resolve above it.
        gradlePluginPortal()
    }
}

rootProject.name = "suld"

// SULD — Mongolian Hardcore MMORPG (PaperMC)
//
// Module layout (see ARCHITECTURE.md):
//   suld-api    Pure-Java domain model, game logic, and service contracts.
//               ZERO Minecraft dependency, so it builds and is unit-tested
//               without a running server.
//   suld-plugin Paper adapter: plugin bootstrap, commands, listeners, Bukkit
//               config bridge, JDBC persistence. Depends on the Paper API,
//               published only at https://repo.papermc.io.
//
// If repo.papermc.io is not reachable from your build environment, you can
// still build and test the whole domain layer with:  ./gradlew :suld-api:build

include("suld-api")
include("suld-plugin")
