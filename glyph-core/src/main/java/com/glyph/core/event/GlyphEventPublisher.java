package com.glyph.core.event;

import com.glyph.api.event.GlyphEventChannels;
import com.glyph.api.event.GlyphEventCodec;
import com.glyph.core.redis.RedisManager;
import java.util.Objects;
import java.util.UUID;

/** Publishes Glyph identity events to Redis for glyph-discord / future consumers. */
public final class GlyphEventPublisher {

    private final RedisManager redis;

    public GlyphEventPublisher(RedisManager redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    public void publishLifetime(UUID playerUuid, long lifetimeEarned) {
        redis.publish(GlyphEventChannels.EVENTS, GlyphEventCodec.lifetime(playerUuid, lifetimeEarned));
    }

    public void publishTitle(UUID playerUuid) {
        redis.publish(GlyphEventChannels.EVENTS, GlyphEventCodec.title(playerUuid));
    }

    public void publishDiscordLinked(UUID playerUuid, long discordUserId) {
        redis.publish(
                GlyphEventChannels.EVENTS,
                GlyphEventCodec.discordLinked(playerUuid, discordUserId));
    }
}
