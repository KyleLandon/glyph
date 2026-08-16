# Runs the Glyph Discord companion.
# Prefer JAVA_HOME; fall back to JDK 25 on this desktop, then PATH java.
# Working directory should be the repo root so secrets.env paths resolve.
# -Hidden: spawn java with no console (dashboard / start-all).
param([switch]$Hidden)

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$root = Split-Path $here -Parent
Set-Location $root

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $jdk25 = "C:\Program Files\Java\jdk-25"
    if (Test-Path (Join-Path $jdk25 "bin\java.exe")) {
        $env:JAVA_HOME = $jdk25
    }
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }

$libs = Join-Path $here "build\libs"
$jar = Get-ChildItem $libs -Filter "glyph-discord-*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch "plain|sources|javadoc" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    Write-Host "Building glyph-discord jar..."
    & (Join-Path $root "gradlew.bat") -p $root :glyph-discord:jar
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $jar = Get-ChildItem $libs -Filter "glyph-discord-*.jar" |
        Where-Object { $_.Name -notmatch "plain|sources|javadoc" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

if (-not $jar) {
    Write-Error "No glyph-discord jar in $libs"
}

Write-Host "Starting $($jar.Name)"
if ($Hidden) {
    $logDir = Join-Path $here "logs"
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $log = Join-Path $logDir "start.log"
    $err = Join-Path $logDir "start.err.log"
    Start-Process -FilePath $java -ArgumentList @("-jar", $jar.FullName) `
        -WorkingDirectory $root -WindowStyle Hidden `
        -RedirectStandardOutput $log -RedirectStandardError $err
    return
}
& $java -jar $jar.FullName
