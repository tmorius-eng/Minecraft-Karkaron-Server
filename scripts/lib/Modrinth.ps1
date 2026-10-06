# Modrinth API v2 client for the SULD scripts (dot-source it). Windows PowerShell 5.1 and PowerShell 7,
# Set-StrictMode -Version Latest safe. Public read/download endpoints only: no token.
#   API docs: https://docs.modrinth.com/api/  (project versions: GET /project/{id|slug}/version)
# Every request sends the project User-Agent, honours HTTP 429 (X-Ratelimit-Reset / Retry-After), and every
# response is shape-checked before a field is used.

$script:ModrinthApi = "https://api.modrinth.com/v2"
$script:ModrinthUserAgent = "tmorius-eng/Minecraft-Karkaron-Server/1.0"
$script:ModrinthMaxAttempts = 5

function Get-MrProp($obj, [string]$name) {
    # StrictMode-safe property read: $null when $obj is not an object or has no such property.
    if ($null -eq $obj -or $obj -is [string] -or $obj -is [ValueType]) { return $null }
    $p = $obj.PSObject.Properties[$name]
    # the comma keeps arrays intact: a bare "return" would unroll a one-element array into its element
    if ($p) { return ,$p.Value }
    return $null
}

function Get-MrDate($value) {
    # date_published is an ISO-8601 string; PowerShell 7's ConvertFrom-Json already turns it into a DateTime.
    if ($value -is [DateTime]) { return $value.ToUniversalTime() }
    $d = [DateTime]::MinValue
    if ($value -is [string] -and [DateTime]::TryParse($value, [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AdjustToUniversal, [ref]$d)) { return $d }
    return $null
}

function ConvertTo-MrList($value) {
    # JSON arrays come back as one object on 5.1; piping enumerates them on 5.1 and 7 alike.
    $list = New-Object System.Collections.Generic.List[object]
    if ($null -ne $value) { $value | ForEach-Object { $list.Add($_) } }
    return ,$list
}

function Get-MrHttpInfo($errorRecord) {
    # Status code and rate-limit wait (seconds) from a failed Invoke-WebRequest, on 5.1 (WebException) and 7 (HttpResponseException).
    $resp = Get-MrProp $errorRecord.Exception "Response"
    $status = 0
    $wait = $null
    if ($resp) {
        $code = Get-MrProp $resp "StatusCode"
        if ($null -ne $code) { $status = [int]$code }
        $headers = Get-MrProp $resp "Headers"
        foreach ($h in @("X-Ratelimit-Reset", "Retry-After")) {
            $v = $null
            if ($headers -is [System.Net.WebHeaderCollection]) {
                $v = $headers[$h]
            } elseif ($headers -and ($headers.PSObject.Methods.Name -contains "TryGetValues")) {
                $vals = $null
                if ($headers.TryGetValues($h, [ref]$vals)) { $v = @($vals)[0] }
            }
            $n = 0
            if ($v -and [int]::TryParse([string]$v, [ref]$n)) { $wait = $n; break }
        }
    }
    return [pscustomobject]@{ Status = $status; Wait = $wait }
}

function Invoke-MrRequest([string]$Uri, [string]$OutFile) {
    # GET with the project User-Agent; retries on HTTP 429 after the server's reset time (bounded), throws otherwise.
    for ($attempt = 1; $attempt -le $script:ModrinthMaxAttempts; $attempt++) {
        # The project User-Agent ("owner/repo/version") is not RFC "product/version" syntax: PowerShell 7 validates
        # header values and rejects it unless told not to; Windows PowerShell 5.1 sends it as is.
        $req = @{ Uri = $Uri; UserAgent = $script:ModrinthUserAgent; UseBasicParsing = $true }
        if ($PSVersionTable.PSVersion.Major -ge 6) { $req["SkipHeaderValidation"] = $true }
        try {
            if ($OutFile) {
                Invoke-WebRequest @req -OutFile $OutFile | Out-Null
                return $null
            }
            $r = Invoke-WebRequest @req -Headers @{ Accept = "application/json" }
            $content = $r.Content
            if ($content -is [byte[]]) { $content = [Text.Encoding]::UTF8.GetString($content) }
            return ($content | ConvertFrom-Json)
        } catch {
            $info = Get-MrHttpInfo $_
            if ($info.Status -ne 429 -or $attempt -eq $script:ModrinthMaxAttempts) { throw }
            $wait = if ($null -ne $info.Wait) { $info.Wait } else { [math]::Pow(2, $attempt) }
            $wait = [math]::Min(60, [math]::Max(1, $wait))
            Write-Host "    .. Modrinth rate limit (HTTP 429); waiting $wait s (attempt $attempt/$($script:ModrinthMaxAttempts))" -ForegroundColor Yellow
            Start-Sleep -Seconds $wait
        }
    }
}

function Test-MrVersionShape($v) {
    # A project-version object as documented (GetProjectVersions): the fields this script relies on, with their types.
    if ($null -eq $v -or $v -is [string] -or $v -is [ValueType]) { return $false }
    foreach ($k in @("id", "version_number", "version_type", "status")) {
        if (-not ((Get-MrProp $v $k) -is [string])) { return $false }
    }
    if ($null -eq (Get-MrDate (Get-MrProp $v "date_published"))) { return $false }
    foreach ($k in @("game_versions", "loaders", "files")) {
        if (-not ((Get-MrProp $v $k) -is [array])) { return $false }
    }
    return $true
}

function Test-MrFileShape($f) {
    if ($null -eq $f -or $f -is [string] -or $f -is [ValueType]) { return $false }
    $url = Get-MrProp $f "url"
    $name = Get-MrProp $f "filename"
    $sha512 = Get-MrProp (Get-MrProp $f "hashes") "sha512"
    return ($url -is [string]) -and $url.StartsWith("https://") -and ($name -is [string]) -and $name.EndsWith(".jar") `
        -and ($name -notmatch '[\\/]') -and ($sha512 -is [string]) -and ($sha512 -match '^[0-9a-fA-F]{128}$')
}

function Get-MrReleases([string]$Project, [string]$GameVersion, [string[]]$Loaders, [string[]]$Types = @("release")) {
    # Listed release versions of a project for this game version and loaders, newest first.
    # Each result: Endpoint, Project, VersionId, Version, File, Url, Sha512, Size, Loaders.
    $games = [uri]::EscapeDataString((ConvertTo-Json -InputObject @($GameVersion) -Compress))
    $load = [uri]::EscapeDataString((ConvertTo-Json -InputObject @($Loaders) -Compress))
    $endpoint = "$($script:ModrinthApi)/project/$([uri]::EscapeDataString($Project))/version?game_versions=$games&loaders=$load&include_changelog=false"
    $json = Invoke-MrRequest $endpoint
    $out = New-Object System.Collections.Generic.List[object]
    if ($null -eq $json -or $json -is [string]) { return ,$out }
    foreach ($v in (ConvertTo-MrList $json)) {
        if (-not (Test-MrVersionShape $v)) { continue }
        if ($Types -notcontains (Get-MrProp $v "version_type")) { continue }
        if ((Get-MrProp $v "status") -ne "listed") { continue }
        $gv = ConvertTo-MrList (Get-MrProp $v "game_versions")
        if (-not ($gv -contains $GameVersion)) { continue }
        $lv = ConvertTo-MrList (Get-MrProp $v "loaders")
        if (-not ($lv | Where-Object { $Loaders -contains $_ })) { continue }
        $files = ConvertTo-MrList (Get-MrProp $v "files")
        $file = $null
        foreach ($f in $files) { if ((Test-MrFileShape $f) -and (Get-MrProp $f "primary") -eq $true) { $file = $f; break } }
        if (-not $file) { foreach ($f in $files) { if (Test-MrFileShape $f) { $file = $f; break } } }
        if (-not $file) { continue }
        $size = Get-MrProp $file "size"
        $out.Add([pscustomobject]@{
            Endpoint = $endpoint; Project = $Project
            VersionId = [string](Get-MrProp $v "id"); Version = [string](Get-MrProp $v "version_number")
            Published = (Get-MrDate (Get-MrProp $v "date_published"))
            File = [string](Get-MrProp $file "filename"); Url = [string](Get-MrProp $file "url")
            Sha512 = ([string](Get-MrProp (Get-MrProp $file "hashes") "sha512")).ToLowerInvariant()
            Size = $(if ($size -is [ValueType]) { [long]$size } else { -1 })
            Loaders = (($lv | ForEach-Object { [string]$_ }) -join ",")
        })
    }
    $sorted = @($out | Sort-Object -Property Published -Descending)
    $result = New-Object System.Collections.Generic.List[object]
    foreach ($r in $sorted) { $result.Add($r) }
    return ,$result
}

function Get-MrPluginReleases([string]$Project, [string]$GameVersion, [bool]$AllowBeta = $false) {
    # Paper builds first; a plugin that only publishes Bukkit/Spigot builds (they run on Paper) as the fallback.
    # Listed releases only, unless the manifest allows beta for a project that publishes no releases (Geyser).
    $types = if ($AllowBeta) { @("release", "beta") } else { @("release") }
    $r = Get-MrReleases $Project $GameVersion @("paper") $types
    if ($r.Count -gt 0) { return ,$r }
    return ,(Get-MrReleases $Project $GameVersion @("spigot", "bukkit") $types)
}

function Save-MrFile($Release, [string]$Destination) {
    # Download to <dest>.part, verify size and SHA-512, then move into place. Returns $true on success.
    $tmp = "$Destination.part"
    if (Test-Path $tmp) { Remove-Item $tmp -Force }
    try {
        Invoke-MrRequest $Release.Url $tmp | Out-Null
    } catch {
        if (Test-Path $tmp) { Remove-Item $tmp -Force }
        Write-Host "    !! download failed: $($Release.Url): $($_.Exception.Message)" -ForegroundColor Yellow
        return $false
    }
    $len = (Get-Item $tmp).Length
    if ($Release.Size -ge 0 -and $len -ne $Release.Size) {
        Remove-Item $tmp -Force
        Write-Host "    !! $($Release.File): size $len != $($Release.Size)" -ForegroundColor Yellow
        return $false
    }
    $hash = (Get-FileHash -Algorithm SHA512 -Path $tmp).Hash.ToLowerInvariant()
    if ($hash -ne $Release.Sha512) {
        Remove-Item $tmp -Force
        Write-Host "    !! $($Release.File): SHA-512 mismatch (got $hash)" -ForegroundColor Yellow
        return $false
    }
    Move-Item $tmp $Destination -Force
    return $true
}

function Test-MrFileHash([string]$Path, $Release) {
    if (-not (Test-Path $Path)) { return $false }
    return ((Get-FileHash -Algorithm SHA512 -Path $Path).Hash.ToLowerInvariant() -eq $Release.Sha512)
}
