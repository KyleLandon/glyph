# Apply GDD Phase 9 world borders via RCON (safe to re-run).
# Requires the Folia backend up with enable-rcon=true.

$ErrorActionPreference = "Stop"
$rcon = Join-Path $PSScriptRoot "rcon.ps1"

function Invoke-Mc([string]$cmd) {
    Write-Output "> $cmd"
    & $rcon $cmd
}

# Diameters: overworld/end 100000 (±50000), nether 12500 (±6250).
Invoke-Mc "execute in minecraft:overworld run worldborder center 0 0"
Invoke-Mc "execute in minecraft:overworld run worldborder set 100000"
Invoke-Mc "execute in minecraft:the_nether run worldborder center 0 0"
Invoke-Mc "execute in minecraft:the_nether run worldborder set 12500"
Invoke-Mc "execute in minecraft:the_end run worldborder center 0 0"
Invoke-Mc "execute in minecraft:the_end run worldborder set 100000"

# Chosen overworld spawn (docs/WORLD.md). spawnRadius 0 lands on the block.
Invoke-Mc "execute in minecraft:overworld run gamerule spawnRadius 0"
Invoke-Mc "execute in minecraft:overworld run setworldspawn -184 70 45"

Write-Output ""
Write-Output "Verify via Chunky (reads live border radius):"
foreach ($world in @("world", "world_nether", "world_the_end")) {
    Invoke-Mc "chunky world $world"
    Invoke-Mc "chunky worldborder"
    Invoke-Mc "chunky selection"
}
Write-Output "Done. Pregen commands: docs/WORLD.md"
