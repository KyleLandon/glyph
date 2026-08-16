package com.glyph.discord;

import com.glyph.discord.commands.AccountLinkService;
import com.glyph.discord.commands.MessageLinkListener;
import com.glyph.discord.commands.SlashCommandListener;
import com.glyph.discord.db.DiscordIdentityRepository;
import com.glyph.discord.listeners.AlphaRoleListener;
import com.glyph.discord.redis.GlyphEventSubscriber;
import com.glyph.discord.roles.RoleBootstrap;
import com.glyph.discord.roles.RoleSyncService;
import com.glyph.discord.staff.StaffGuideService;
import com.glyph.discord.staff.StaffGuideTopic;
import com.glyph.discord.staff.StaffHelpListener;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.EnumSet;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Glyph Discord companion bot.
 *
 * <p>Required: token + guild id (env or {@code secrets.env}). Roles are
 * auto-created on first boot when missing. See {@code docs/DISCORD.md}.</p>
 */
public final class GlyphDiscordMain {

    private GlyphDiscordMain() {
    }

    public static void main(String[] args) throws Exception {
        Logger logger = LoggerFactory.getLogger(GlyphDiscordMain.class);
        DiscordBotConfig bootstrapConfig = DiscordBotConfig.load();

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(bootstrapConfig.jdbcUrl());
        hikari.setUsername(bootstrapConfig.dbUser());
        hikari.setPassword(bootstrapConfig.dbPassword());
        hikari.setMaximumPoolSize(5);
        hikari.setPoolName("glyph-discord-pg");
        HikariDataSource dataSource = new HikariDataSource(hikari);
        DiscordIdentityRepository repository = new DiscordIdentityRepository(dataSource);

        RedisURI.Builder redisUri = RedisURI.builder()
                .withHost(bootstrapConfig.redisHost())
                .withPort(bootstrapConfig.redisPort());
        if (bootstrapConfig.hasRedisPassword()) {
            redisUri.withPassword(bootstrapConfig.redisPassword().toCharArray());
        }
        RedisClient redisClient = RedisClient.create(redisUri.build());
        StatefulRedisConnection<String, String> redisConnection = redisClient.connect();

        boolean messageContent =
                Boolean.parseBoolean(System.getenv().getOrDefault("GLYPH_DISCORD_MESSAGE_CONTENT", "false"));
        EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MEMBERS);
        if (messageContent) {
            intents.add(GatewayIntent.GUILD_MESSAGES);
            intents.add(GatewayIntent.MESSAGE_CONTENT);
        }

        JDABuilder builder = JDABuilder.createDefault(bootstrapConfig.token())
                .enableIntents(intents);
        JDA jda = builder.build();
        jda.awaitReady();

        Guild guild = jda.getGuildById(bootstrapConfig.guildId());
        if (guild == null) {
            throw new IllegalStateException(
                    "Bot is not in guild " + bootstrapConfig.guildId()
                            + " — invite it first (bot + applications.commands).");
        }

        DiscordBotConfig config = RoleBootstrap.ensure(guild, bootstrapConfig, logger);
        RoleSyncService roleSync = new RoleSyncService(config, repository, logger);
        AccountLinkService links = new AccountLinkService(
                repository, roleSync, redisConnection.sync(), logger);

        StaffGuideService staffGuide = new StaffGuideService(logger);
        jda.addEventListener(
                new SlashCommandListener(config, repository, links, logger),
                new StaffHelpListener(staffGuide, logger),
                new AlphaRoleListener(config, repository, logger));
        if (messageContent) {
            jda.addEventListener(new MessageLinkListener(links));
            logger.info("Plain-text /link CODE fallback enabled (MESSAGE_CONTENT)");
        } else {
            logger.info(
                    "Plain-text /link fallback off — enable Message Content Intent in the Discord "
                            + "portal, then set GLYPH_DISCORD_MESSAGE_CONTENT=true");
        }

        OptionData staffTopic = new OptionData(
                OptionType.STRING, "topic", "Jump straight to a topic", false);
        for (StaffGuideTopic topic : StaffGuideTopic.values()) {
            staffTopic.addChoice(topic.emoji() + " " + topic.title(), topic.id());
        }

        // Guild commands are available immediately (global can take up to ~1 hour).
        guild.updateCommands().addCommands(
                Commands.slash("link", "Link your Discord to your Glyph Minecraft account")
                        .addOption(OptionType.STRING, "code", "Code from /linkdiscord in-game", true),
                Commands.slash("alpha", "Grant or revoke Glyph Alpha server access")
                        .addSubcommands(
                                new SubcommandData("grant", "Grant alpha access")
                                        .addOption(OptionType.USER, "user", "Discord user", true),
                                new SubcommandData("revoke", "Revoke alpha access")
                                        .addOption(OptionType.USER, "user", "Discord user", true)),
                Commands.slash("staffhelp", "Ephemeral Glyph staff handbook")
                        .addOptions(staffTopic),
                Commands.slash("staffguide", "Publish / refresh the #staff-guide forum")
                        .addSubcommands(new SubcommandData(
                                "setup", "Create or update forum posts with staff embeds"))
        ).complete();
        jda.updateCommands().complete();
        logger.info("Registered guild slash commands for {}", guild.getName());

        GlyphEventSubscriber subscriber = new GlyphEventSubscriber(config, roleSync, jda, logger);
        try {
            for (DiscordIdentityRepository.LinkedAccount link : repository.findAllLinked()) {
                roleSync.syncForMinecraft(guild, link.minecraftUuid());
            }
        } catch (Exception e) {
            logger.warn("Startup role sync for linked accounts failed", e);
        }
        logger.info("GlyphDiscord online for guild {}", config.guildId());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                subscriber.close();
            } catch (Exception ignored) {
                // ignore
            }
            jda.shutdown();
            redisConnection.close();
            redisClient.shutdown();
            dataSource.close();
        }));
    }
}
