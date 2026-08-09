# Glyph Folia backend (GDD Phase 7, section 105).
#
# The server jar is downloaded at build time from the PaperMC downloads API
# and pinned to the exact version/build we test against locally
# (glyph-folia/). Bump the args together with the local jar.
#
# Runtime state (world, plugins, configs) lives in /data — mount a volume.
# Plugin jars are NOT baked into the image: deploy them into the volume so
# a plugin update does not require an image rebuild.

FROM eclipse-temurin:25-jre

ARG FOLIA_VERSION=26.1.2
ARG FOLIA_BUILD=8

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /opt/folia.jar \
    "https://api.papermc.io/v2/projects/folia/versions/${FOLIA_VERSION}/builds/${FOLIA_BUILD}/downloads/folia-${FOLIA_VERSION}-${FOLIA_BUILD}.jar"

RUN useradd --system --uid 1000 --create-home minecraft
USER minecraft
WORKDIR /data

# Memory is the only knob most hosts need; Aikar's flags mirror
# glyph-folia/start.ps1 so container and desktop behave identically.
ENV MEMORY=4G
ENV JVM_FLAGS="-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
    -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M \
    -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 \
    -XX:+PerfDisableSharedMem -Dusing.aikars.flags=https://mcflags.emc.gs \
    -Daikars.new.flags=true"

EXPOSE 25565/tcp 24454/udp

# EULA must be accepted explicitly via the environment (EULA=true).
CMD ["bash", "-c", "\
    if [ \"$EULA\" != \"true\" ]; then \
        echo 'Set EULA=true to accept the Minecraft EULA (https://aka.ms/MinecraftEULA).' >&2; \
        exit 1; \
    fi; \
    echo 'eula=true' > eula.txt; \
    exec java -Xms$MEMORY -Xmx$MEMORY $JVM_FLAGS -jar /opt/folia.jar --nogui"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/25565' || exit 1
