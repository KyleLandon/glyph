# First-time Paper SMP backend: download Paper 26.1.2 and copy the shared
# plugin jars from Folia (LuckPerms, VaultUnlocked, voicechat). GlyphCore
# is deployed by scripts\build_glyphcore.ps1.
#
# Folia-only plugins (Chunky, JustEnoughRecipes, spark) stay on anarchy.
# After the first Paper start, run scripts\sync-forwarding-secret.ps1.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$smp = Join-Path $root "glyph-smp"
$foliaPlugins = Join-Path $root "glyph-folia\plugins"
$smpPlugins = Join-Path $smp "plugins"
New-Item -ItemType Directory -Force -Path $smpPlugins | Out-Null

$paper = Get-ChildItem -Path $smp -Filter "paper-*.jar" -File -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $paper) {
    $ua = "glyph-setup-smp/0.1 (https://glyphmc.net)"
    $api = "https://fill.papermc.io/v3/projects/paper/versions/26.1.2/builds"
    Write-Host "Downloading Paper 26.1.2..."
    $builds = Invoke-RestMethod -Uri $api -Headers @{ "User-Agent" = $ua }
    $stable = @($builds) | Where-Object { $_.channel -eq "STABLE" } | Select-Object -First 1
    if (-not $stable) { $stable = @($builds) | Select-Object -First 1 }
    $url = $stable.downloads."server:default".url
    if (-not $url) { Write-Error "Paper download URL missing from $api" }
    $buildId = if ($stable.build) { [string]$stable.build } else { "stable" }
    $dest = Join-Path $smp ("paper-26.1.2-" + $buildId + ".jar")
    Invoke-WebRequest -Uri $url -OutFile $dest -Headers @{ "User-Agent" = $ua }
    Write-Host "Saved $dest"
} else {
    Write-Host "Paper jar already present: $($paper.Name)"
}

function Copy-SharedJar([string]$pattern, [string]$label) {
    $src = Get-ChildItem -Path $foliaPlugins -Filter $pattern -File -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $src) {
        Write-Host "Skipped $label : no $pattern in glyph-folia/plugins" -ForegroundColor Yellow
        return
    }
    Copy-Item $src.FullName -Destination $smpPlugins -Force
    Write-Host "Copied $($src.Name)"
}

Copy-SharedJar "LuckPerms-Bukkit-*.jar" "LuckPerms"
Copy-SharedJar "VaultUnlocked-*.jar" "VaultUnlocked"
Copy-SharedJar "voicechat-bukkit-*.jar" "voicechat"

$gp = Get-ChildItem -Path $smpPlugins -Filter "GriefPrevention*.jar" -File -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $gp) {
    $gpUrl = "https://cdn.modrinth.com/data/O4o4mKaq/versions/dGfCZHqk/GriefPrevention.jar"
    Write-Host "Downloading GriefPrevention 16.18.7..."
    Invoke-WebRequest -Uri $gpUrl -OutFile (Join-Path $smpPlugins "GriefPrevention-16.18.7.jar") -Headers @{
        "User-Agent" = "glyph-setup-smp/0.1 (https://glyphmc.net)"
    }
    Write-Host "Saved GriefPrevention-16.18.7.jar"
} else {
    Write-Host "GriefPrevention already present: $($gp.Name)"
}

$gpConfig = Join-Path $smpPlugins "GriefPreventionData\config.yml"
if (Test-Path $gpConfig) {
    $gpc = Get-Content $gpConfig -Raw
    $gpc = $gpc -replace '(?m)^    InitialBlocks: \d+\s*$', '    InitialBlocks: 10000'
    $gpc = $gpc -replace '(?m)(Claim Blocks Accrued Per Hour:\r?\n      Default: )\d+', '${1}500'
    $gpc = $gpc -replace '(?m)(Max Accrued Claim Blocks:\r?\n      Default: )\d+', '${1}100000'
    Set-Content -Path $gpConfig -Value $gpc -Encoding UTF8 -NoNewline
    Write-Host "GriefPrevention: 10000 starting claim blocks, 500 / hour, max 100000"
}

$lpSrc = Join-Path $foliaPlugins "LuckPerms\config.yml"
$lpDestDir = Join-Path $smpPlugins "LuckPerms"
$lpDest = Join-Path $lpDestDir "config.yml"
if (Test-Path $lpSrc) {
    New-Item -ItemType Directory -Force -Path $lpDestDir | Out-Null
    $lp = Get-Content $lpSrc -Raw
    $lp = $lp -replace '(?m)^server: anarchy\s*$', 'server: smp'
    Set-Content -Path $lpDest -Value $lp -Encoding UTF8
    Write-Host "LuckPerms config: server smp, same Postgres as Folia"
} else {
    Write-Host "Skipped LuckPerms config: $lpSrc missing" -ForegroundColor Yellow
}

$permSrc = Join-Path $root "glyph-folia\permissions.yml"
$permDest = Join-Path $smp "permissions.yml"
if (Test-Path $permSrc) {
    Copy-Item $permSrc -Destination $permDest -Force
    Write-Host "Copied permissions.yml (voicechat speak/listen/groups for everyone)"
}

$vcSrc = Join-Path $foliaPlugins "voicechat\voicechat-server.properties"
$vcDestDir = Join-Path $smpPlugins "voicechat"
$vcDest = Join-Path $vcDestDir "voicechat-server.properties"
if (Test-Path $vcSrc) {
    New-Item -ItemType Directory -Force -Path $vcDestDir | Out-Null
    $vc = Get-Content $vcSrc -Raw
    $vc = $vc -replace '(?m)^port=24454\s*$', 'port=24455'
    $vc = $vc -replace '(?m)^voice_host=.*$', 'voice_host='
    $vc = $vc -replace '(?m)^threaded_server_support=true\s*$', 'threaded_server_support=false'
    Set-Content -Path $vcDest -Value $vc -Encoding UTF8
    Write-Host "voicechat UDP 24455 (Folia keeps 24454)"
}

Write-Host "Next: scripts\build_glyphcore.ps1"
Write-Host "Then start Paper once (glyph-smp\start.ps1), stop it, scripts\sync-forwarding-secret.ps1"
