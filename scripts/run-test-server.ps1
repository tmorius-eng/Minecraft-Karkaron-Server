<#
.SYNOPSIS
    Local SULD development test server for Windows 11 (PowerShell port of scripts/run-test-server.sh).

.DESCRIPTION
    1. Checks for Java 21.
    2. Builds the plugin (gradlew.bat :suld-plugin:shadowJar).
    3. Downloads the latest Paper build for the Minecraft version, via the PaperMC Fill v3 API,
       and verifies its SHA-256.
    4. Prepares run\: installs the plugin, writes eula.txt and the test server.properties.
    5. Starts Paper with a 2 GB maximum heap.

    The script is safe to run repeatedly:
    - the Paper jar is downloaded only when missing or corrupt;
    - old SULD jars are replaced;
    - only the test keys in server.properties are updated, the rest is kept;
    - it refuses to start when a server is already running on the port.

    LOCAL DEVELOPMENT ONLY. This server runs with online-mode=false and enables SULD's
    auth.allow-insecure-offline-dev-mode, so anyone who can connect can join under any name.
    It binds to 127.0.0.1 by default for that reason. This is NOT a production configuration:
    production uses online-mode=true (see deploy\ and docs\AUTHENTICATION.md).

.PARAMETER MinecraftVersion
    Paper / Minecraft version (default 1.21.11).

.PARAMETER Port
    Server port (default 25565).

.PARAMETER Lan
    Listen on all interfaces instead of 127.0.0.1 (other PCs on your LAN can join).
    Offline mode means they can join under any name. Use only on a trusted network.

.PARAMETER SkipBuild
    Reuse the last built plugin jar instead of running Gradle.

.PARAMETER Plugins
    Which trusted third-party plugin profiles from deploy\plugins\plugins.json to install into run\plugins
    (default "core": LuckPerms, EssentialsX, VaultUnlocked, PlaceholderAPI, CoreProtect, WorldEdit,
    WorldGuard, spark, Chunky, DiscordSRV). "core,hardening" adds GrimAC, Plan, LibertyBans, ViaVersion.
    "none" installs no third-party plugins. Only builds made for this Minecraft version are installed.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run-test-server.ps1
#>
[CmdletBinding()]
param(
    [string]$MinecraftVersion = "1.21.11",
    [int]$Port = 25565,
    [switch]$Lan,
    [switch]$SkipBuild,
    [string]$Plugins = "core"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # Invoke-WebRequest is very slow with the progress bar
# Windows PowerShell 5.1 defaults to old TLS versions; the PaperMC API requires TLS 1.2+.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$RunDir = Join-Path $Root "run"
$PluginsDir = Join-Path $RunDir "plugins"
$Api = "https://fill.papermc.io/v3/projects/paper"
$UserAgent = "SULD-dev-test-server/1.0 (local development script)"
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Write-Step([string]$Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Fail([string]$Message) {
    Write-Host "ERROR: $Message" -ForegroundColor Red
    exit 1
}

# --- 1. Java 21 ---------------------------------------------------------------------------------
function Get-JavaMajor([string]$JavaExe) {
    # `java -version` prints to stderr; capture it directly (no shell quoting involved).
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo($JavaExe, "-version")
        $psi.UseShellExecute = $false
        $psi.RedirectStandardError = $true
        $psi.RedirectStandardOutput = $true
        $psi.CreateNoWindow = $true
        $p = [System.Diagnostics.Process]::Start($psi)
        $out = $p.StandardError.ReadToEnd() + $p.StandardOutput.ReadToEnd()
        $p.WaitForExit()
    } catch {
        return 0
    }
    if ($out -match 'version "(\d+)(\.(\d+))?') {
        $major = [int]$Matches[1]
        if ($major -eq 1 -and $Matches[3]) { $major = [int]$Matches[3] }   # "1.8.0" style
        return $major
    }
    return 0
}

Write-Step "Checking for Java 21"
$java = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if ((Get-JavaMajor $candidate) -eq 21) { $java = $candidate }
}
if (-not $java) {
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath -and (Get-JavaMajor $onPath.Source) -eq 21) { $java = $onPath.Source }
}
if (-not $java) {
    # Common install locations (Temurin, Microsoft OpenJDK, Oracle, Zulu).
    $roots = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Java", "$env:ProgramFiles\Zulu")
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        foreach ($dir in Get-ChildItem $r -Directory -Filter "*21*" -ErrorAction SilentlyContinue) {
            $candidate = Join-Path $dir.FullName "bin\java.exe"
            if ((Test-Path $candidate) -and (Get-JavaMajor $candidate) -eq 21) { $java = $candidate; break }
        }
        if ($java) { break }
    }
}
if (-not $java) {
    Fail ("Java 21 not found. Install it, e.g.:`n" +
          "    winget install --id EclipseAdoptium.Temurin.21.JDK -e`n" +
          "then open a new PowerShell window (or set JAVA_HOME to the JDK 21 folder) and re-run.")
}
$JavaHome = Split-Path (Split-Path $java -Parent) -Parent
$env:JAVA_HOME = $JavaHome          # Gradle uses the same JDK
Write-Host "    Java 21: $java"

# --- refuse to start a second server ------------------------------------------------------------
$listening = $null
try { $listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue } catch { }
if ($listening) {
    Fail "Port $Port is already in use. Is a server already running? Stop it first (type 'stop' in its console)."
}

# --- 2. build the plugin ------------------------------------------------------------------------
$libs = Join-Path $Root "suld-plugin\build\libs"
if (-not $SkipBuild) {
    Write-Step "Building SULD plugin (:suld-plugin:shadowJar)"
    Push-Location $Root
    try {
        & (Join-Path $Root "gradlew.bat") ":suld-plugin:shadowJar" "--console=plain"
        if ($LASTEXITCODE -ne 0) { Fail "Gradle build failed (exit code $LASTEXITCODE)." }
    } finally {
        Pop-Location
    }
}
$pluginJar = Get-ChildItem $libs -Filter "suld-plugin-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|dev|plain)\.jar$' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $pluginJar) { Fail "No plugin jar in $libs. Run without -SkipBuild." }
Write-Host "    Plugin: $($pluginJar.Name)"

# --- 3. Paper -----------------------------------------------------------------------------------
Write-Step "Resolving the latest Paper build for $MinecraftVersion"
try {
    $build = Invoke-RestMethod -Uri "$Api/versions/$MinecraftVersion/builds/latest" -UserAgent $UserAgent
} catch {
    Fail "Could not query the PaperMC API for $MinecraftVersion ($($_.Exception.Message))."
}
$entry = $null
if ($build.PSObject.Properties["downloads"]) { $entry = $build.downloads.PSObject.Properties["server:default"] }
if (-not $entry) { Fail "The PaperMC API returned no server download for $MinecraftVersion." }
$download = $entry.Value
$jarName = $download.name
$jarUrl = $download.url
$sha256 = $null
if ($download.PSObject.Properties["checksums"]) { $sha256 = $download.checksums.sha256 }

New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null
$paperJar = Join-Path $RunDir $jarName

function Test-Sha256([string]$Path, [string]$Expected) {
    if (-not $Expected) { return $true }
    return ((Get-FileHash -Algorithm SHA256 -Path $Path).Hash -ieq $Expected)
}

if ((Test-Path $paperJar) -and -not (Test-Sha256 $paperJar $sha256)) {
    Write-Host "    $jarName is corrupt or incomplete; downloading it again."
    Remove-Item $paperJar -Force
}
if (-not (Test-Path $paperJar)) {
    Write-Step "Downloading $jarName"
    $tmp = "$paperJar.download"
    try {
        Invoke-WebRequest -Uri $jarUrl -OutFile $tmp -UserAgent $UserAgent -UseBasicParsing
    } catch {
        if (Test-Path $tmp) { Remove-Item $tmp -Force }
        Fail "Download failed: $($_.Exception.Message)"
    }
    if (-not (Test-Sha256 $tmp $sha256)) {
        Remove-Item $tmp -Force
        Fail "SHA-256 mismatch for $jarName. The download was not used."
    }
    Move-Item $tmp $paperJar -Force
} else {
    Write-Host "    $jarName already present (checksum OK)."
}
# Keep only the current Paper jar.
Get-ChildItem $RunDir -Filter "paper-*.jar" | Where-Object { $_.Name -ne $jarName } | Remove-Item -Force

# --- 3b. trusted third-party plugins ------------------------------------------------------------
function Get-Prop($obj, [string]$name) {
    # StrictMode-safe property read (returns $null when absent)
    if ($null -eq $obj) { return $null }
    $p = $obj.PSObject.Properties[$name]
    if ($p) { return $p.Value }
    return $null
}

function ConvertTo-List($json) {
    # Windows PowerShell 5.1 returns a JSON array from Invoke-RestMethod as ONE object;
    # piping it through ForEach-Object enumerates the elements on 5.1 and 7 alike.
    $list = New-Object System.Collections.Generic.List[object]
    if ($null -ne $json) { $json | ForEach-Object { $list.Add($_) } }
    return ,$list
}

function Get-PluginBuilds($entry, [string]$Mc) {
    # Candidate builds for this Minecraft version, best first (releases newest first, then the rest).
    $out = New-Object System.Collections.Generic.List[object]
    $slug = Get-Prop $entry "modrinth"
    if ($slug) {
        $loaders = [uri]::EscapeDataString('["paper","purpur","spigot","bukkit"]')
        $games = [uri]::EscapeDataString("[`"$Mc`"]")
        $versions = $null
        try {
            $versions = ConvertTo-List (Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$slug/version?loaders=$loaders&game_versions=$games" -UserAgent $UserAgent)
        } catch { $versions = $null }
        if ($versions) {
            $ordered = @($versions | Where-Object { (Get-Prop $_ "version_type") -eq "release" }) + @($versions | Where-Object { (Get-Prop $_ "version_type") -ne "release" })
            foreach ($v in $ordered) {
                $f = $null
                foreach ($candidate in (ConvertTo-List (Get-Prop $v "files"))) { if (Get-Prop $candidate "primary") { $f = $candidate; break } }
                if (-not $f) { $f = (ConvertTo-List (Get-Prop $v "files")) | Select-Object -First 1 }
                if ($f) {
                    $out.Add([pscustomobject]@{ Version = [string](Get-Prop $v "version_number"); File = [string](Get-Prop $f "filename");
                        Url = [string](Get-Prop $f "url"); Algo = "SHA512"; Hash = [string](Get-Prop (Get-Prop $f "hashes") "sha512"); Source = "modrinth" })
                }
                if ($out.Count -ge 6) { break }
            }
        }
    }
    $hangar = Get-Prop $entry "hangar"
    if ($hangar) {
        $parts = $hangar.Split("/")
        try {
            $data = Invoke-RestMethod -Uri "https://hangar.papermc.io/api/v1/projects/$($parts[0])/$($parts[1])/versions?platform=PAPER&platformVersion=$Mc&limit=5" -UserAgent $UserAgent
            foreach ($v in (ConvertTo-List (Get-Prop $data "result"))) {
                $d = Get-Prop (Get-Prop $v "downloads") "PAPER"
                if (-not $d) { continue }
                $url = Get-Prop $d "downloadUrl"
                if (-not $url) { $url = Get-Prop $d "externalUrl" }
                if (-not $url) { continue }
                $info = Get-Prop $d "fileInfo"
                $name = Get-Prop $info "name"
                if (-not $name) { $name = "$($parts[1])-$(Get-Prop $v 'name').jar" }
                $out.Add([pscustomobject]@{ Version = [string](Get-Prop $v "name"); File = [string]$name; Url = [string]$url;
                    Algo = "SHA256"; Hash = [string](Get-Prop $info "sha256Hash"); Source = "hangar" })
            }
        } catch { }
    }
    return ,$out
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-JarInfo([string]$Path) {
    # plugin name and the Java class-file version of its main class (65 = Java 21)
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $yml = $zip.GetEntry("plugin.yml")
        if (-not $yml) { $yml = $zip.GetEntry("paper-plugin.yml") }
        if (-not $yml) { return $null }
        $r = New-Object IO.StreamReader($yml.Open())
        try { $text = $r.ReadToEnd() } finally { $r.Dispose() }
        $name = if ($text -match '(?m)^name:\s*["'']?([^"''\r\n]+)') { $Matches[1].Trim() } else { $null }
        $main = if ($text -match '(?m)^main:\s*["'']?([^"''\r\n]+)') { $Matches[1].Trim() } else { $null }
        $major = 0
        if ($main) {
            $cls = $zip.GetEntry(($main -replace '\.', '/') + ".class")
            if ($cls) {
                $s = $cls.Open()
                try { $buf = New-Object byte[] 8; [void]$s.Read($buf, 0, 8); $major = $buf[6] * 256 + $buf[7] } finally { $s.Dispose() }
            }
        }
        return [pscustomobject]@{ Name = $name; Major = $major }
    } finally {
        $zip.Dispose()
    }
}

$JavaClassMax = 65   # Paper 1.21.11 runs on Java 21

function Remove-StrayPlugins([string]$Dir, $Lock, $Managed) {
    # remove duplicates of managed plugins (e.g. jars left by an older installer) and jars built for a newer Java
    $keep = @{}
    foreach ($k in $Lock.Keys) { $keep[[string](Get-Prop $Lock[$k] "file")] = $true }
    foreach ($jar in Get-ChildItem $Dir -Filter "*.jar") {
        if ($keep.ContainsKey($jar.Name) -or $jar.Name -like "suld-plugin-*") { continue }
        $info = $null
        try { $info = Get-JarInfo $jar.FullName } catch { }
        $pluginName = if ($info) { $info.Name } else { $null }
        $aliases = @{ "Essentials" = "EssentialsX"; "Vault" = "VaultUnlocked" }
        $managedName = if ($pluginName -and $aliases.ContainsKey($pluginName)) { $aliases[$pluginName] } else { $pluginName }
        if (($managedName -and $Managed.ContainsKey($managedName)) -or ($info -and $info.Major -gt $JavaClassMax)) {
            Write-Host "    xx removing stray $($jar.Name) ($pluginName)" -ForegroundColor Yellow
            Remove-Item $jar.FullName -Force
        }
    }
}

if ($Plugins -ne "none") {
    Write-Step "Installing trusted plugins ($Plugins) for Minecraft $MinecraftVersion"
    $manifest = Get-Content (Join-Path $Root "deploy\plugins\plugins.json") -Raw | ConvertFrom-Json
    $profiles = $Plugins.Split(",") | ForEach-Object { $_.Trim() }
    New-Item -ItemType Directory -Force -Path $PluginsDir | Out-Null
    $lockFile = Join-Path $PluginsDir ".suld-plugins.lock.json"
    $lock = @{}
    if (Test-Path $lockFile) {
        (Get-Content $lockFile -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $lock[$_.Name] = $_.Value }
    }
    $managed = @{}
    $skipped = @()
    foreach ($p in (ConvertTo-List $manifest.plugins)) {
        if ($profiles -notcontains $p.profile) { continue }
        $managed[$p.name] = $true
        $prev = $lock[$p.id]
        $prevFile = [string](Get-Prop $prev "file")
        $installed = $null
        foreach ($build in (Get-PluginBuilds $p $MinecraftVersion)) {
            $dest = Join-Path $PluginsDir $build.File
            if (Test-Path $dest) {
                $info = Get-JarInfo $dest
                if ($info -and $info.Major -le $JavaClassMax) { $installed = $build; break }
                Remove-Item $dest -Force   # present but built for a newer Java: not usable here
                continue
            }
            $tmp = "$dest.part"
            try {
                Invoke-WebRequest -Uri $build.Url -OutFile $tmp -UserAgent $UserAgent -UseBasicParsing
            } catch {
                if (Test-Path $tmp) { Remove-Item $tmp -Force }
                continue
            }
            if ($build.Hash -and ((Get-FileHash -Algorithm $build.Algo -Path $tmp).Hash -ine $build.Hash)) {
                Remove-Item $tmp -Force
                Write-Host "    !! $($p.name) $($build.Version): $($build.Algo) mismatch" -ForegroundColor Yellow
                continue
            }
            $info = Get-JarInfo $tmp
            if (-not $info -or $info.Major -gt $JavaClassMax) {
                Remove-Item $tmp -Force
                Write-Host "    .. $($p.name) $($build.Version) needs a newer Java; trying an older build"
                continue
            }
            Move-Item $tmp $dest -Force
            $installed = $build
            break
        }
        if (-not $installed) { $skipped += $p.name; Write-Host "    -- $($p.name): no Java 21 build for $MinecraftVersion (skipped)"; continue }
        if ($prevFile -and $prevFile -ne $installed.File) {
            $old = Join-Path $PluginsDir $prevFile
            if (Test-Path $old) { Remove-Item $old -Force }
        }
        $lock[$p.id] = [pscustomobject]@{ name = $p.name; version = $installed.Version; file = $installed.File; source = $installed.Source }
        Write-Host "    ok $($p.name) $($installed.Version) ($($installed.Source))"
    }
    Remove-StrayPlugins $PluginsDir $lock $managed
    [IO.File]::WriteAllText($lockFile, ($lock | ConvertTo-Json -Depth 4), $Utf8NoBom)
    if ($skipped.Count -gt 0) { Write-Host "    Not installed: $($skipped -join ', ')" -ForegroundColor Yellow }
}

# --- 4. run directory ---------------------------------------------------------------------------
Write-Step "Installing plugin, accepting EULA, writing test server.properties"
Get-ChildItem $PluginsDir -Filter "suld-plugin-*.jar" | Remove-Item -Force
Copy-Item $pluginJar.FullName (Join-Path $PluginsDir $pluginJar.Name) -Force

# Write without a BOM: a BOM in front of "eula=true" makes Minecraft ignore the line.
[IO.File]::WriteAllText((Join-Path $RunDir "eula.txt"), "eula=true`n", $Utf8NoBom)

# server.properties: set the test keys and keep every other line Paper wrote.
$props = [ordered]@{
    "online-mode"      = "false"           # LOCAL DEV ONLY, see the header
    "server-ip"        = $(if ($Lan) { "" } else { "127.0.0.1" })
    "server-port"      = "$Port"
    "motd"             = "SULD local test server"
    "max-players"      = "10"
    "view-distance"    = "6"
    "simulation-distance" = "6"
    "spawn-protection" = "0"
    "enforce-secure-profile" = "false"   # offline clients have no signed chat keys
}
$propsFile = Join-Path $RunDir "server.properties"
$lines = New-Object System.Collections.Generic.List[string]
if (Test-Path $propsFile) {
    foreach ($line in [IO.File]::ReadAllLines($propsFile)) {
        $key = ($line -split "=", 2)[0].Trim()
        if (-not $line.StartsWith("#") -and $props.Contains($key)) { continue }
        $lines.Add($line)
    }
} else {
    $lines.Add("# SULD LOCAL DEVELOPMENT test server (scripts/run-test-server.ps1). NOT for production.")
}
foreach ($k in $props.Keys) { $lines.Add("$k=$($props[$k])") }
[IO.File]::WriteAllText($propsFile, (($lines -join "`n") + "`n"), $Utf8NoBom)

# SULD refuses every login on an offline-mode server unless its local-dev flag is set
# (fail closed). Enable that flag in this run directory only. The repository's config.yml
# keeps the safe default (false).
$suldDir = Join-Path $PluginsDir "SULD"
$suldConfig = Join-Path $suldDir "config.yml"
New-Item -ItemType Directory -Force -Path $suldDir | Out-Null
if (-not (Test-Path $suldConfig)) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($pluginJar.FullName)
    try {
        $entry = $zip.GetEntry("config.yml")
        if (-not $entry) { Fail "config.yml not found inside $($pluginJar.Name)." }
        $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8)
        try { $yaml = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally {
        $zip.Dispose()
    }
} else {
    $yaml = [IO.File]::ReadAllText($suldConfig, [Text.Encoding]::UTF8)
}
$flagPattern = '(?m)^(\s*allow-insecure-offline-dev-mode:\s*)\S+'
if ($yaml -notmatch $flagPattern) { Fail "auth.allow-insecure-offline-dev-mode not found in SULD config.yml." }
$yaml = [regex]::Replace($yaml, $flagPattern, '${1}true')
[IO.File]::WriteAllText($suldConfig, $yaml, $Utf8NoBom)

# --- 5. start -----------------------------------------------------------------------------------
$address = if ($Lan) { "<this PC's LAN IP>:$Port" } else { "localhost:$Port" }
Write-Host ""
Write-Host "LOCAL DEVELOPMENT SERVER: offline mode, insecure dev auth. Never expose it to the internet." -ForegroundColor Yellow
Write-Step "Starting Paper ($jarName) with -Xmx2G. Connect to $address. Type 'stop' to shut down."
Push-Location $RunDir
try {
    & $java "-Xms1G" "-Xmx2G" "-jar" $jarName "--nogui"
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $code
