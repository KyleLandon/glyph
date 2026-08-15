# Chains Chunky pregen after the current overworld task finishes.
# Overworld should already be running (10k around spawn). When done:
#   nether = full worldborder (±6250)
#   end    = 5000 around 0,0
#
# Usage: run minimized / background while Folia is up.
#   powershell -File scripts\pregen-continue.ps1

$ErrorActionPreference = "Stop"
$rcon = Join-Path $PSScriptRoot "rcon.ps1"

function Invoke-Mc([string]$cmd) {
    & $rcon $cmd 2>&1 | Out-String
}

function Get-ProgressText {
    (Invoke-Mc "chunky progress").Trim()
}

function Wait-UntilIdle {
    param([string]$Label)
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
        if ($p -match 'Task running') {
            if ($p -match 'Processed:.*?(\d+\.?\d*%)') {
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Label $($Matches[1])"
            } else {
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Label still running"
            }
            continue
        }
        # Idle / unknown — treat as finished if not explicitly running
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Progress: $p"
        if ($p -notmatch 'Task running') { return }
    }
}

Write-Host "Pregen continue watcher started."
Wait-UntilIdle "overworld"

Write-Host "Starting nether worldborder pregen..."
@(
    "chunky world world_nether",
    "chunky worldborder",
    "chunky silent",
    "chunky start"
) | ForEach-Object {
    Write-Host "> $_"
    Invoke-Mc $_ | Write-Host
}
Wait-UntilIdle "nether"

Write-Host "Starting end radius 5000 pregen..."
@(
    "chunky world world_the_end",
    "chunky shape square",
    "chunky center 0 0",
    "chunky radius 5000",
    "chunky silent",
    "chunky start"
) | ForEach-Object {
    Write-Host "> $_"
    Invoke-Mc $_ | Write-Host
}
Wait-UntilIdle "end"

Write-Host "All staged pregen tasks finished."
$world = Join-Path (Split-Path $PSScriptRoot -Parent) "glyph-folia\world"
$sum = (Get-ChildItem $world -Recurse -File -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
Write-Host ("Overworld disk now: {0:N1} MB" -f ($sum / 1MB))
