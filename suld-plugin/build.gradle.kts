// suld-plugin — the real Paper plugin: bootstrap, services, commands, listeners,
// Bukkit config bridge, and JDBC persistence. This is where SULD meets Minecraft.
//
// It depends on the clean domain layer (:suld-api) and the Paper API. All
// gameplay logic is hand-written here; no ready-made gameplay plugins are used.
// The only third-party libraries are infrastructure: HikariCP (connection pool,
// shaded) and the JDBC drivers (loaded at runtime via plugin.yml `libraries`).

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    // Spins up a real Paper test server with `./gradlew :suld-plugin:runServer`.
    // Downloads the server jar from Paper's download API at task time, so it
    // requires network access to api.papermc.io / fill.papermc.io.
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

// Pinned to the current stable Paper. The latest `<release>` in the Paper repo
// is a prerelease under Paper's new versioning scheme; this is the newest full
// release. Override with -PpaperApiVersion=... / -PminecraftVersion=... .
val paperApiVersion = findProperty("paperApiVersion") as String? ?: "1.21.11-R0.1-SNAPSHOT"
val minecraftVersion = findProperty("minecraftVersion") as String? ?: "1.21.11"

repositories {
    // PaperMC — the ONLY source of the Paper API (not on Maven Central). Scoped
    // to this module, which is the only one that needs it. See DEPENDENCIES.md.
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    // the HUD composition (Adventure components, no server) is unit-tested
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")

    // Our own domain layer — not published to any repository, so it is shaded
    // into the final jar.
    implementation(project(":suld-api"))

    // Infrastructure: database connection pool (shaded). JDBC drivers are NOT
    // bundled; they are declared in src/main/resources/plugin.yml under
    // `libraries:` so only the one the admin configures is fetched at runtime.
    implementation("com.zaxxer:HikariCP:5.1.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Test-only: JDBC integration tests against a real PostgreSQL (skipped unless
    // SULD_TEST_PG_URL is set). Same pinned version plugin.yml loads at runtime.
    testImplementation("org.postgresql:postgresql:42.7.4")
}

// ---------------------------------------------------------------------------------------------------------------
// Progression simulator (docs/PROGRESSION_SIMULATION.md). A separate source set: it never ships in the plugin jar
// (shadowJar takes `main` only) and needs no Paper API, so it builds without repo.papermc.io. It compiles against
// suld-api plus a *copy* of the Bukkit-free content classes, so the live model reads the real mobs, dungeons and
// story instead of a transcription that could drift.
// ---------------------------------------------------------------------------------------------------------------
val syncSimContent = tasks.register<Sync>("syncSimContent") {
    from("src/main/java/mn/suld/plugin/content")
    into(layout.buildDirectory.dir("generated/simContent/java/mn/suld/plugin/content"))
}

val sim: SourceSet = sourceSets.create("sim") {
    java.srcDir("src/sim/java")
    java.srcDir(layout.buildDirectory.dir("generated/simContent/java"))
}
val simTestSet: SourceSet = sourceSets.create("simTest") {
    compileClasspath += sim.output
    runtimeClasspath += sim.output
}
dependencies {
    add(sim.implementationConfigurationName, project(":suld-api"))
    add(simTestSet.implementationConfigurationName, project(":suld-api"))
    add(simTestSet.implementationConfigurationName, platform("org.junit:junit-bom:5.11.3"))
    add(simTestSet.implementationConfigurationName, "org.junit.jupiter:junit-jupiter")
    add(simTestSet.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
}
tasks.named(sim.compileJavaTaskName) { dependsOn(syncSimContent) }

val simTest = tasks.register<Test>("simTest") {
    description = "Golden and compliance tests of the progression simulator (quick mode)."
    group = "verification"
    testClassesDirs = simTestSet.output.classesDirs
    classpath = simTestSet.runtimeClasspath
    maxHeapSize = "2g"
}
tasks.named("check") { dependsOn(simTest) }

// ./gradlew :suld-plugin:simulate            full run -> audit/progression-balance.json + docs tables
// ./gradlew :suld-plugin:simulate -Pquick    small Monte-Carlo for a fast look
tasks.register<JavaExec>("simulate") {
    description = "Runs the progression simulation (live vs proposed rules) and writes the reports."
    group = "application"
    classpath = sim.runtimeClasspath
    mainClass.set("mn.suld.sim.Sim")
    workingDir = rootProject.projectDir
    maxHeapSize = "3g"
    args = listOfNotNull("--out", "audit/progression-balance.json", "--doc", "docs/PROGRESSION_SIMULATION.md",
        if (project.hasProperty("quick")) "--quick" else null)
}

// The SÜLD resource pack, zipped reproducibly (fixed timestamps, stable order) and bundled into the
// plugin jar, so the server can self-host it with a stable SHA-1.
val resourcePackZip = tasks.register<Zip>("resourcePackZip") {
    from(rootProject.file("resourcepack")) {
        exclude("**/*.md", "**/.gitkeep")
    }
    archiveFileName.set("suld-resourcepack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Relocate shaded libraries to avoid clashing with other plugins/Paper.
        relocate("com.zaxxer.hikari", "mn.suld.lib.hikari")
        minimize {
            // Keep HikariCP intact; minimization can strip reflectively-used bits.
            exclude(dependency("com.zaxxer:HikariCP:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        // Forward the opt-in DB integration-test settings (never required for a normal build).
        listOf("SULD_TEST_PG_URL", "SULD_TEST_PG_USER", "SULD_TEST_PG_PASS").forEach { key ->
            System.getenv(key)?.let { environment(key, it) }
        }
    }

    runServer {
        minecraftVersion(minecraftVersion)
    }

    // Make the plugin jar's resources (plugin.yml) available to processResources.
    processResources {
        val props = mapOf("version" to project.version, "apiVersion" to "1.21")
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
        // World assets (slices, points, schematics) ship inside the jar: the server needs no
        // world-editing plugin and no external files to build Kharkhorum.
        from(resourcePackZip)
        from(rootProject.file("assets/world")) {
            into("world")
            exclude("**/*.png", "**/*.md")
        }
    }
}

tasks.register<JavaExec>("simProbe") {
    description = "Development aid: one simulated trajectory (-Pargs='live hardcore baatar 30')."
    group = "application"
    classpath = sim.runtimeClasspath
    mainClass.set("mn.suld.sim.Probe")
    workingDir = rootProject.projectDir
    args = ((findProperty("args") as String?) ?: "live hardcore baatar 30").split(" ")
}

tasks.register<JavaExec>("simCalibrate") {
    description = "Development aid: median player power by level (proposed rules)."
    group = "application"
    classpath = sim.runtimeClasspath
    mainClass.set("mn.suld.sim.Calibrate")
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("simTune") {
    description = "Development aid: bisects the curve base for ~200 efficient hours to level 60."
    group = "application"
    classpath = sim.runtimeClasspath
    mainClass.set("mn.suld.sim.Sim")
    workingDir = rootProject.projectDir
    maxHeapSize = "3g"
    args = listOf("--tune")
}

tasks.register<JavaExec>("specTables") {
    description = "Writes the exact proposed numbers into the spec documents."
    group = "application"
    classpath = sim.runtimeClasspath
    mainClass.set("mn.suld.sim.SpecTables")
    workingDir = rootProject.projectDir
}
tasks.named("simulate") { finalizedBy("specTables") }
