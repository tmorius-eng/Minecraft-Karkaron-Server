# SULD Dependency & Repository Audit

Governance: prefer Maven Central; prefer PaperMC for Paper artifacts; avoid
JitPack; avoid snapshots in production; pin versions; no silently-added repos;
third-party libraries are **infrastructure-only** (never a gameplay system).

## Repositories

| Repository | URL | Used for | Justification |
|---|---|---|---|
| Maven Central | `https://repo1.maven.org/maven2/` (`mavenCentral()`) | HikariCP, JUnit, annotations, build-plugin libs | Default, preferred source of record. |
| Maven Central mirror (Google) | `https://maven-central.storage-download.googleapis.com/maven2/` | *same artifacts as Central* | **Not a third-party repo** — Google's byte-identical read-only mirror of Central. Listed first **only** because this build's shared egress IP is rate-limited by canonical Central (consistent HTTP 429), which breaks resolution (incl. Plugin-Portal JAR downloads that redirect to Central). On an unrated network, `mavenCentral()` alone suffices and this line can be removed. |
| PaperMC | `https://repo.papermc.io/repository/maven-public/` | `io.papermc.paper:paper-api` | The Paper API is published **only** here; it is not on Maven Central. Scoped to `:suld-plugin`. |
| Gradle Plugin Portal | `gradlePluginPortal()` | Shadow & run-paper plugin markers | Standard home of Gradle plugins; markers exist nowhere else. |

No JitPack. No other third-party repositories.

## Runtime / compile dependencies (pinned)

| Dependency | Version | Scope | Category | Notes |
|---|---|---|---|---|
| `io.papermc.paper:paper-api` | `1.21.11-R0.1-SNAPSHOT` | compileOnly | Platform API | Provided by the server at runtime; **not shipped** in our jar. Paper publishes its API exclusively as a `-SNAPSHOT`; this is the one unavoidable snapshot and is not a production runtime dependency. |
| `com.zaxxer:HikariCP` | `5.1.0` | implementation (shaded) | Infrastructure (DB pool) | Relocated to `mn.suld.lib.hikari` to avoid clashes. |
| `com.mysql:mysql-connector-j` | `9.1.0` | `plugin.yml` `libraries:` | Infrastructure (JDBC driver) | Fetched by Paper from Central at runtime; only used if MySQL is configured. |
| `org.postgresql:postgresql` | `42.7.4` | `plugin.yml` `libraries:` | Infrastructure (JDBC driver) | As above, for PostgreSQL. |
| `org.jetbrains:annotations` | `24.1.0` | compileOnly | Utility (nullability annotations) | Compile-time only. |
| `org.junit:junit-bom` / `junit-jupiter` | `5.11.3` | testImplementation | Testing | — |
| `org.junit.platform:junit-platform-launcher` | (BOM-managed) | testRuntimeOnly | Testing | — |

### Transitively provided by Paper (not declared by us)

Adventure (text/UI), Gson, SLF4J, Brigadier, Guava, SnakeYAML, etc. are part of
the Paper runtime and are used via the platform, never bundled.

## Build tooling (Gradle plugins, pinned)

| Plugin | Version | Purpose |
|---|---|---|
| `com.gradleup.shadow` | `8.3.5` | Shade `suld-api` + HikariCP into the plugin jar. |
| `xyz.jpenilla.run-paper` | `2.3.1` | `runServer` task for a local Paper test server. |
| Gradle wrapper | `8.14.3` | Reproducible build. |

## Gameplay plugins

**None, by rule.** Every gameplay system is implemented in this repository. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the blocklist and rationale.
