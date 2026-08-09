# Builds the plugins and copies them into the local test server directories.
#   glyph-core  -> glyph-folia/plugins/    (local Folia test server)
#   glyph-proxy -> glyph-velocity/plugins/ (local Velocity test proxy, if present)
$root = Split-Path -Parent $PSScriptRoot

& "$root\gradlew.bat" -p $root build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$foliaPlugins = Join-Path $root "glyph-folia\plugins"
if (Test-Path $foliaPlugins) {
    Get-ChildItem "$root\glyph-core\build\libs\glyph-core-*.jar" |
        Copy-Item -Destination $foliaPlugins -Force
    Write-Host "Deployed glyph-core to $foliaPlugins" -ForegroundColor Green
} else {
    Write-Host "Skipped Folia deploy: $foliaPlugins not found" -ForegroundColor Yellow
}

$velocityPlugins = Join-Path $root "glyph-velocity\plugins"
if (Test-Path $velocityPlugins) {
    Get-ChildItem "$root\glyph-proxy\build\libs\glyph-proxy-*.jar" |
        Copy-Item -Destination $velocityPlugins -Force
    Write-Host "Deployed glyph-proxy to $velocityPlugins" -ForegroundColor Green
} else {
    Write-Host "Skipped Velocity deploy: $velocityPlugins not found" -ForegroundColor Yellow
}
