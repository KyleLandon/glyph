# Glyph Velocity proxy (GDD Phase 7, section 105): the public entry point.
#
# The proxy jar is downloaded at build time from the PaperMC downloads API,
# pinned to the version/build we test against locally (glyph-velocity/).
#
# Runtime state (velocity.toml, forwarding.secret, plugins) lives in /data —
# mount a volume and put the same forwarding.secret the backend uses there
# (see scripts/sync-forwarding-secret.ps1 for the desktop equivalent).

FROM eclipse-temurin:25-jre

ARG VELOCITY_VERSION=4.1.0-SNAPSHOT
ARG VELOCITY_BUILD=16

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /opt/velocity.jar \
    "https://api.papermc.io/v2/projects/velocity/versions/${VELOCITY_VERSION}/builds/${VELOCITY_BUILD}/downloads/velocity-${VELOCITY_VERSION}-${VELOCITY_BUILD}.jar"

RUN useradd --system --uid 1000 --create-home minecraft
USER minecraft
WORKDIR /data

# Flags mirror glyph-velocity/start.ps1.
ENV MEMORY=512M
ENV JVM_FLAGS="-XX:+UseG1GC -XX:G1HeapRegionSize=4M \
    -XX:+UnlockExperimentalVMOptions -XX:+ParallelRefProcEnabled \
    -XX:+AlwaysPreTouch"

EXPOSE 25565/tcp

CMD ["bash", "-c", "exec java -Xms$MEMORY -Xmx$MEMORY $JVM_FLAGS -jar /opt/velocity.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/25565' || exit 1
