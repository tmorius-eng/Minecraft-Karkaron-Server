// suld-api — the shared SULD domain model, game logic, and service contracts.
//
// HARD RULE: this module must NOT depend on Bukkit/Paper/Spigot or on any
// third-party gameplay plugin. It is pure Java so the entire gameplay core can
// be unit-tested without a running Minecraft server. Infrastructure/utility
// libraries (serialization, testing) are allowed; gameplay logic is hand-written.

plugins {
    `java-library`
}

dependencies {
    // Annotations only (compile-time); keeps the API self-documenting without a
    // heavy runtime dependency.
    compileOnly("org.jetbrains:annotations:24.1.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Offline world tooling: dump modules/slices for rendering and validate slices (no server needed).
//   ./gradlew -q :suld-api:worldTool --args="module gate.imperial /tmp/gate.txt"
tasks.register<JavaExec>("worldTool") {
    group = "world"
    description = "Kharkhorum module/slice dump and validation"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("mn.suld.api.worldbuild.kharkhorum.KharkhorumTool")
    workingDir = rootProject.projectDir
}
