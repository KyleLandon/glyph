# Starts the local Velocity proxy (public entry point on :25565).
& java `
    "-Xms512M" "-Xmx512M" `
    "-XX:+UseG1GC" `
    "-XX:G1HeapRegionSize=4M" `
    "-XX:+UnlockExperimentalVMOptions" `
    "-XX:+ParallelRefProcEnabled" `
    "-XX:+AlwaysPreTouch" `
    -jar velocity-4.1.0-SNAPSHOT-16.jar
