# Starts the local Paper SMP backend.
# Same wallet (Postgres) as Folia anarchy; separate world and inventory.
# -Hidden: spawn java with no console (dashboard / start-all / /restart).
param([switch]$Hidden)

$mem = "4G"

$jar = Get-ChildItem -Path $PSScriptRoot -Filter "paper-*.jar" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) {
    Write-Error "No paper-*.jar in $PSScriptRoot. Run scripts\setup-smp.ps1 first."
}

# Prefer JAVA_HOME: the PATH java may be an old JDK (e.g. 11 on the desktop).
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
$javaArgs = @(
    "-Xms$mem", "-Xmx$mem",
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=200",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+DisableExplicitGC",
    "-XX:+AlwaysPreTouch",
    "-XX:G1NewSizePercent=30",
    "-XX:G1MaxNewSizePercent=40",
    "-XX:G1HeapRegionSize=8M",
    "-XX:G1ReservePercent=20",
    "-XX:G1HeapWastePercent=5",
    "-XX:G1MixedGCCountTarget=4",
    "-XX:InitiatingHeapOccupancyPercent=15",
    "-XX:G1MixedGCLiveThresholdPercent=90",
    "-XX:G1RSetUpdatingPauseTimePercent=5",
    "-XX:SurvivorRatio=32",
    "-XX:+PerfDisableSharedMem",
    "-XX:MaxTenuringThreshold=1",
    "-Dusing.aikars.flags=https://mcflags.emc.gs",
    "-Daikars.new.flags=true",
    "-jar", $jar.Name, "--nogui"
)
if ($Hidden) {
    Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
    return
}
& $java @javaArgs
