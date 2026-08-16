# Builds Glyph plugins and copies them into the local test servers.
#   glyph-core  -> glyph-folia/plugins/    (or plugins/update/ if Folia is up)
#               -> glyph-smp/plugins/      (or plugins/update/ if Paper is up)
#   glyph-proxy -> glyph-velocity/plugins/
#
# Never overwrite a live Folia jar - that breaks the plugin classloader.
#
# Usage:
#   scripts\build_glyphcore.ps1
#   scripts\build_glyphcore.ps1 -Restart   # build, then /restart via RCON
param(
    [switch]$Restart
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

& "$root\gradlew.bat" -p $root build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

function Deploy-PluginJar([string]$sourceGlob, [string]$destDir, [string]$label) {
    if (-not (Test-Path $destDir)) {
        Write-Host "Skipped $label deploy: $destDir not found" -ForegroundColor Yellow
        return
    }
    $jars = Get-ChildItem $sourceGlob
    if (-not $jars) {
        Write-Host "Skipped $label deploy: no jars matching $sourceGlob" -ForegroundColor Yellow
        return
    }
    $jars | Copy-Item -Destination $destDir -Force
    Write-Host "Deployed $label to $destDir" -ForegroundColor Green
}

function Deploy-GlyphCore([string]$pluginsDir, [int]$port, [string]$label) {
    if (-not (Test-Path $pluginsDir)) {
        Write-Host "Skipped $label : $pluginsDir not found" -ForegroundColor Yellow
        return
    }
    $running = [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    if ($running) {
        $updateDir = Join-Path $pluginsDir "update"
        New-Item -ItemType Directory -Force -Path $updateDir | Out-Null
        Deploy-PluginJar "$root\glyph-core\build\libs\glyph-core-*.jar" $updateDir "$label (staged for restart)"
    } else {
        Deploy-PluginJar "$root\glyph-core\build\libs\glyph-core-*.jar" $pluginsDir $label
    }
}

Deploy-GlyphCore (Join-Path $root "glyph-folia\plugins") 25566 "glyph-core (anarchy)"
Deploy-GlyphCore (Join-Path $root "glyph-smp\plugins") 25567 "glyph-core (smp)"

$velocityPlugins = Join-Path $root "glyph-velocity\plugins"
Deploy-PluginJar "$root\glyph-proxy\build\libs\glyph-proxy-*.jar" $velocityPlugins "glyph-proxy"

$foliaRunning = [bool](Get-NetTCPConnection -LocalPort 25566 -State Listen -ErrorAction SilentlyContinue)
$smpRunning = [bool](Get-NetTCPConnection -LocalPort 25567 -State Listen -ErrorAction SilentlyContinue)

if ($Restart) {
    if (-not $foliaRunning -and -not $smpRunning) {
        Write-Host "No backend is up - start Folia or Paper first" -ForegroundColor Yellow
        exit 0
    }
    if ($foliaRunning) {
        Write-Host "Restarting Folia (10s countdown)..." -ForegroundColor Cyan
        & "$PSScriptRoot\rcon.ps1" "restart"
    }
    if ($smpRunning) {
        Write-Host "Restarting SMP (10s countdown)..." -ForegroundColor Cyan
        & "$PSScriptRoot\rcon.ps1" -Target smp "restart"
    }
    exit $LASTEXITCODE
}

if ($foliaRunning -or $smpRunning) {
    Write-Host "Staged. In game: /restart   or   scripts\build_glyphcore.ps1 -Restart" -ForegroundColor Yellow
}
