package com.glyph.discord.redis;

import com.glyph.api.event.GlyphEventChannels;
import com.glyph.api.event.GlyphEventCodec;
import com.glyph.api.event.GlyphEventType;
import com.glyph.discord.DiscordBotConfig;
import com.glyph.discord.roles.RoleSyncService;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.util.Objects;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;

/** Subscribes to {@code glyph.events} and syncs Discord prestige roles. */
public final class GlyphEventSubscriber implements AutoCloseable {

    private final DiscordBotConfig config;
    private final RoleSyncService roleSync;
    private final JDA jda;
    private final Logger logger;
    private final RedisClient client;
    private final StatefulRedisPubSubConnection<String, String> connection;

    public GlyphEventSubscriber(
            DiscordBotConfig config, RoleSyncService roleSync, JDA jda, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.roleSync = Objects.requireNonNull(roleSync, "roleSync");
        this.jda = Objects.requireNonNull(jda, "jda");
        this.logger = Objects.requireNonNull(logger, "logger");

        RedisURI.Builder uri = RedisURI.builder()
                .withHost(config.redisHost())
                .withPort(config.redisPort());
        if (config.hasRedisPassword()) {
            uri.withPassword(config.redisPassword().toCharArray());
        }
        this.client = RedisClient.create(uri.build());
        this.connection = client.connectPubSub();
        connection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                onMessage(message);
            }
        });
        connection.sync().subscribe(GlyphEventChannels.EVENTS);
        logger.info("Subscribed to Redis channel {}", GlyphEventChannels.EVENTS);
    }

    private void onMessage(String message) {
        try {
            GlyphEventType type = GlyphEventCodec.typeOf(message).orElse(null);
            if (type == null) {
                return;
            }
            Guild guild = jda.getGuildById(config.guildId());
            if (guild == null) {
                logger.warn("Guild {} not available for event {}", config.guildId(), type);
                return;
            }
            switch (type) {
                case GLYPH_LIFETIME -> GlyphEventCodec.parseLifetime(message).ifPresent(event ->
                        roleSync.syncForMinecraft(guild, event.uuid()));
                case GLYPH_TITLE -> GlyphEventCodec.parseTitle(message).ifPresent(event ->
                        roleSync.syncForMinecraft(guild, event.uuid()));
                case DISCORD_LINKED -> GlyphEventCodec.parseDiscordLinked(message).ifPresent(event ->
                        roleSync.syncForMinecraft(guild, event.uuid()));
            }
        } catch (Exception e) {
            logger.error("Failed handling Redis event: {}", message, e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (Exception ignored) {
            // ignore
        }
        client.shutdown();
    }
}
