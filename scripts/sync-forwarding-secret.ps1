# Wires the Velocity forwarding secret into the Folia backend config.
#
# The secret and glyph-folia/config/paper-global.yml are NOT in git (public
# repo). On a new machine:
#   1. Start glyph-velocity once (generates forwarding.secret), stop it.
#   2. Start glyph-folia once (generates config/paper-global.yml), stop it.
#   3. Run this script, then start both normally.
#
# It enables Velocity modern forwarding in paper-global.yml and injects the
# secret. Idempotent.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

$secretFile = Join-Path $root "glyph-velocity\forwarding.secret"
$paperGlobal = Join-Path $root "glyph-folia\config\paper-global.yml"

if (-not (Test-Path $secretFile)) {
    Write-Error "Missing $secretFile — start the Velocity proxy once first."
}
if (-not (Test-Path $paperGlobal)) {
    Write-Error "Missing $paperGlobal — start the Folia server once first."
}

$secret = (Get-Content $secretFile -Raw).Trim()
$lines = Get-Content $paperGlobal

# Patch only inside the proxies.velocity block (bungee-cord also has an
# online-mode key we must not touch).
$inVelocity = $false
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\s{2}velocity:') { $inVelocity = $true; continue }
    if ($inVelocity -and $lines[$i] -match '^\s{0,2}\S') { $inVelocity = $false }
    if ($inVelocity) {
        $lines[$i] = $lines[$i] -replace '^(\s*)enabled:.*', '$1enabled: true'
        $lines[$i] = $lines[$i] -replace '^(\s*)online-mode:.*', '$1online-mode: true'
        $lines[$i] = $lines[$i] -replace '^(\s*)secret:.*', ('$1secret: ' + $secret)
    }
}

Set-Content $paperGlobal $lines -Encoding UTF8
Write-Host "OK: paper-global.yml now has velocity forwarding enabled with the proxy secret."
