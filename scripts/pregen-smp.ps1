# Pregen Forever World around spawn, then tell BlueMap to render the new chunks.
# Requires Chunky on Paper (copied by setup-smp.ps1) and an SMP RCON connection.
#
#   powershell -File scripts\pregen-smp.ps1
#   powershell -File scripts\pregen-smp.ps1 -Radius 5000
#
# Default is a 2500-block square around world spawn (4748, -1766). That is
# enough for the market / hang-out loop. Pause with: rcon -Target smp "chunky pause"

param(
    [int]$Radius = 2500,
    [int]$CenterX = 4748,
    [int]$CenterZ = -1766
)

$ErrorActionPreference = "Stop"
$rcon = Join-Path $PSScriptRoot "rcon.ps1"

function Invoke-Smp([string]$cmd) {
    & $rcon -Target smp $cmd 2>&1 | Out-String
}

function Get-ProgressText {
    (Invoke-Smp "chunky progress").Trim()
}

function Wait-UntilIdle([string]$Label) {
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Waiting for $Label to finish..."
    while ($true) {
        Start-Sleep -Seconds 60
        $p = Get-ProgressText
        if (-not $p) {
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] No progress output; assuming idle."
            return
        }
        if ($p -match 'No tasks running' -or $p -match 'complete' -or $p -match 'Finished') {
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Label done: $p"
            return
        }
        if ($p -match 'Processed:.*?(\d+\.?\d*%)') {
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Label $($Matches[1])"
            continue
        }
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Progress: $p"
        if ($p -notmatch 'Task running') { return }
    }
}

Write-Host "Starting Forever World pregen: square radius $Radius at $CenterX $CenterZ"
@(
    "chunky world world",
    "chunky shape square",
    "chunky center $CenterX $CenterZ",
    "chunky radius $Radius",
    "chunky silent",
    "chunky start"
) | ForEach-Object {
    Write-Host "> $_"
    Invoke-Smp $_ | Write-Host
}

Wait-UntilIdle "overworld"

Write-Host "Asking BlueMap to render newly generated chunks..."
Invoke-Smp "bluemap update world" | Write-Host
Write-Host "Pregen finished. BlueMap will keep rendering in the background."
Write-Host "Map: https://map.glyphmc.net"
