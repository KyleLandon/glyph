# Starts the local Velocity proxy (public entry point on :25565).
# Prefer JAVA_HOME: the PATH java may be an old JDK (e.g. 11 on the desktop).
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
& $java `
    "-Xms512M" "-Xmx512M" `
    "-XX:+UseG1GC" `
    "-XX:G1HeapRegionSize=4M" `
    "-XX:+UnlockExperimentalVMOptions" `
    "-XX:+ParallelRefProcEnabled" `
    "-XX:+AlwaysPreTouch" `
    -jar velocity-4.1.0-SNAPSHOT-16.jar
