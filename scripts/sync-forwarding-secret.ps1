# Wires the Velocity forwarding secret into each Paper/Folia backend config.
#
# The secret and */config/paper-global.yml are NOT in git (public repo).
# On a new machine:
#   1. Start glyph-velocity once (generates forwarding.secret), stop it.
#   2. Start glyph-folia once (generates config/paper-global.yml), stop it.
#   3. Start glyph-smp once after setup-smp.ps1 (same), stop it.
#   4. Run this script, then start the stack normally.
#
# It enables Velocity modern forwarding in paper-global.yml and injects the
# secret. Idempotent. Skips a backend whose paper-global.yml is not there yet.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

$secretFile = Join-Path $root "glyph-velocity\forwarding.secret"
if (-not (Test-Path $secretFile)) {
    Write-Error "Missing $secretFile - start the Velocity proxy once first."
}
$secret = (Get-Content $secretFile -Raw).Trim()

function Set-VelocityForwarding([string]$paperGlobal) {
    if (-not (Test-Path $paperGlobal)) {
        Write-Host "Skipped (start that backend once first): $paperGlobal" -ForegroundColor Yellow
        return
    }
    $lines = Get-Content $paperGlobal
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
    Write-Host "OK: $paperGlobal has velocity forwarding enabled."
}

Set-VelocityForwarding (Join-Path $root "glyph-folia\config\paper-global.yml")
Set-VelocityForwarding (Join-Path $root "glyph-smp\config\paper-global.yml")
