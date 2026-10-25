# One-time move to a new host (docs/MINI_PC.md), step 1 of 2: run on the OLD
# machine. Stops the network cleanly, dumps PostgreSQL, and copies everything
# git does not carry into a bundle folder: worlds, BlueMap, CoreProtect, SMP
# jars, secrets, Cloudflare tunnel credentials and the Glyph user env vars.
# Copy the bundle to the new machine and run scripts\migrate-import.ps1 there.
#
#   .\migrate-export.ps1 -Destination E:\glyph-migration
#   .\migrate-export.ps1 -Destination E:\glyph-migration -Retire
#
# -Retire also disables this machine's Glyph scheduled tasks and the
# cloudflared service (service part needs Administrator) so the old host
# does not bring the network back after a reboot. Undo with
# Enable-ScheduledTask / Set-Service if the move is abandoned.
#
# The bundle holds secrets (DB passwords, Discord token, Cloudflare token and
# tunnel credentials). Keep it on a private drive and delete it afterwards.

param(
    [Parameter(Mandatory = $true)][string]$Destination,
    [switch]$Retire
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$repoOut = Join-Path $Destination "repo"
New-Item -ItemType Directory -Path $repoOut -Force | Out-Null

# Docker Desktop is per-user and not on PATH in every context.
$dockerBin = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin"
if (Test-Path $dockerBin) { $env:PATH = "$dockerBin;" + $env:PATH }

# --- 1. Stop the network ----------------------------------------------------
function Get-ListenerPid([int]$port) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($conn) { return $conn.OwningProcess }
    return $null
}

# Proxy first so nobody reconnects mid-save. Velocity holds no state.
$proxyPid = Get-ListenerPid 25565
if ($proxyPid) {
    Stop-Process -Id $proxyPid -Force
    Write-Output "Stopped Velocity (pid $proxyPid)"
}

# Backends: RCON "stop" saves every world; wait for the JVM itself to exit
# (the port closes before the final chunk save finishes).
foreach ($backend in @(@{ Target = "folia"; Port = 25566 }, @{ Target = "smp"; Port = 25567 })) {
    $javaPid = Get-ListenerPid $backend.Port
    if (-not $javaPid) { continue }
    & "$PSScriptRoot\rcon.ps1" -Target $backend.Target "stop" | Out-Null
    Write-Output "Stopping $($backend.Target) (pid $javaPid), waiting for the world save..."
    Wait-Process -Id $javaPid -Timeout 300 -ErrorAction SilentlyContinue
    if (Get-Process -Id $javaPid -ErrorAction SilentlyContinue) {
        Write-Error "$($backend.Target) did not exit within 5 minutes - not copying a live world."
    }
}

Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match "glyph-discord-\S+\.jar|GlyphDiscordMain" } |
    ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force
        Write-Output "Stopped Discord bot (pid $($_.ProcessId))"
    }

# --- 2. PostgreSQL dump -------------------------------------------------------
# Dump from the data volume itself. If the compose container is not running
# (e.g. another project's container holds 5432), start a throwaway postgres on
# the same volume with no published port. Never run both at once on one volume.
$volume = "glyph-dev_glyph-postgres-data"
$running = docker ps --format "{{.Names}}" | Select-String -Quiet "^glyph-dev-postgres$"
$container = "glyph-dev-postgres"
$temporary = $false
if (-not $running) {
    $container = "glyph-migrate-pg"
    try { docker rm -f $container 2>$null | Out-Null } catch {}
    docker run -d --name $container -v "${volume}:/var/lib/postgresql/data" postgres:17-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Error "Could not start a postgres container on $volume" }
    $temporary = $true
}
$deadline = (Get-Date).AddMinutes(2)
$ready = $false
do {
    Start-Sleep -Seconds 2
    try { docker exec $container pg_isready -U glyph_app -d glyph 2>$null | Out-Null; $ready = $LASTEXITCODE -eq 0 } catch {}
} until ($ready -or (Get-Date) -gt $deadline)
if (-not $ready) { Write-Error "PostgreSQL in $container did not become ready." }

$dbFile = Join-Path $Destination "glyph-db.dump"
docker exec $container pg_dump -U glyph_app -Fc -f /tmp/glyph-migrate.dump glyph
if ($LASTEXITCODE -ne 0) { Write-Error "pg_dump failed (exit $LASTEXITCODE)" }
docker cp "${container}:/tmp/glyph-migrate.dump" $dbFile | Out-Null
docker exec $container rm /tmp/glyph-migrate.dump
if ($temporary) { docker rm -f $container | Out-Null }
Write-Output ("Database: {0} ({1:N1} KB)" -f $dbFile, ((Get-Item $dbFile).Length / 1KB))

# --- 3. Files git does not carry ---------------------------------------------
$dirs = @(
    "glyph-smp\bluemap",
    "glyph-smp\plugins\BlueMap",
    "glyph-smp\plugins\CoreProtect",
    "glyph-smp\plugins\Vault"
)
foreach ($server in @("glyph-folia", "glyph-smp")) {
    Get-ChildItem (Join-Path $root $server) -Directory -Filter "world*" |
        ForEach-Object { $dirs += "$server\$($_.Name)" }
}
foreach ($dir in $dirs) {
    $src = Join-Path $root $dir
    if (-not (Test-Path $src)) { Write-Output "Skipped (missing): $dir"; continue }
    Write-Output "Copying $dir..."
    robocopy $src (Join-Path $repoOut $dir) /E /R:1 /W:1 /XF session.lock /NFL /NDL /NJH /NJS | Out-Null
    if ($LASTEXITCODE -ge 8) { Write-Error "robocopy failed for $dir (exit $LASTEXITCODE)" }
}

$files = @(
    ".env",
    "glyph-core\src\main\java\com\glyph\core\discord\secrets.env",
    "glyph-velocity\forwarding.secret",
    "glyph-folia\config\paper-global.yml",
    "glyph-smp\config\paper-global.yml",
    "glyph-folia\usercache.json",
    "glyph-smp\usercache.json"
)
$files += Get-ChildItem (Join-Path $root "glyph-smp") -Filter "*.jar" -File | ForEach-Object { "glyph-smp\$($_.Name)" }
$files += Get-ChildItem (Join-Path $root "glyph-smp\plugins") -Filter "*.jar" -File | ForEach-Object { "glyph-smp\plugins\$($_.Name)" }
foreach ($file in $files) {
    $src = Join-Path $root $file
    if (-not (Test-Path $src)) { Write-Output "Skipped (missing): $file"; continue }
    $dest = Join-Path $repoOut $file
    New-Item -ItemType Directory -Path (Split-Path $dest -Parent) -Force | Out-Null
    Copy-Item $src $dest -Force
}
Write-Output "Copied $($files.Count) config/secret/jar files"

$cfDir = Join-Path $env:USERPROFILE ".cloudflared"
if (Test-Path $cfDir) {
    robocopy $cfDir (Join-Path $Destination "cloudflared") /E /R:1 /W:1 /NFL /NDL /NJH /NJS | Out-Null
    Write-Output "Copied Cloudflare tunnel credentials"
}

# User-level env vars the scripts read (DDNS token, playit switch).
$envNames = @("CLOUDFLARE_API_TOKEN", "GLYPH_DNS_ZONE", "GLYPH_DNS_RECORD", "GLYPH_USE_PLAYIT")
$userEnv = [ordered]@{}
foreach ($name in $envNames) {
    $value = [Environment]::GetEnvironmentVariable($name, "User")
    if ($value) { $userEnv[$name] = $value }
}
$userEnv | ConvertTo-Json | Set-Content (Join-Path $Destination "user-env.json") -Encoding ASCII
Write-Output "Saved $($userEnv.Count) user env vars"

# --- 4. Retire this host -------------------------------------------------------
if ($Retire) {
    foreach ($task in @("Glyph Servers", "Glyph Auto Pull", "Glyph DDNS", "Glyph Backup")) {
        if (Get-ScheduledTask -TaskName $task -ErrorAction SilentlyContinue) {
            Disable-ScheduledTask -TaskName $task | Out-Null
            Write-Output "Disabled task: $task"
        }
    }
    $cfSvc = Get-Service cloudflared -ErrorAction SilentlyContinue
    if ($cfSvc) {
        try {
            Stop-Service cloudflared
            Set-Service cloudflared -StartupType Disabled
            Write-Output "Stopped and disabled cloudflared service"
        } catch {
            Write-Output "Could not disable cloudflared (run as Administrator) - the map tunnel will have two connectors until you do"
        }
    }
}

$size = (Get-ChildItem $Destination -Recurse -File | Measure-Object Length -Sum).Sum
Write-Output ("Bundle ready: {0} ({1:N1} GB). Next: copy it to the new machine and run scripts\migrate-import.ps1 -Source <folder>" -f $Destination, ($size / 1GB))
