package com.glyph.discord.roles;

import com.glyph.api.discord.DiscordTier;
import com.glyph.api.glyphs.GlyphTitle;
import com.glyph.discord.DiscordBotConfig;
import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.slf4j.Logger;

/**
 * Ensures Glyph Discord roles exist — uses configured IDs when set, otherwise
 * finds by name or creates them (Administrator / Manage Roles required).
 */
public final class RoleBootstrap {

    public static final String VERIFIED_NAME = "Verified";
    public static final String ALPHA_NAME = "Glyph Alpha";

    private RoleBootstrap() {
    }

    public static DiscordBotConfig ensure(Guild guild, DiscordBotConfig config, Logger logger) {
        Objects.requireNonNull(guild, "guild");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(logger, "logger");

        long verified = ensureRole(
                guild, config.verifiedRoleId(), VERIFIED_NAME, new Color(0x9E9E9E), false, logger);

        Map<DiscordTier, Long> tiers = new EnumMap<>(DiscordTier.class);
        for (DiscordTier tier : DiscordTier.values()) {
            long configured = config.tierRoleIds().getOrDefault(tier, 0L);
            long roleId = ensureRole(
                    guild,
                    configured,
                    displayName(tier),
                    colorFor(tier),
                    true,
                    logger);
            tiers.put(tier, roleId);
        }

        long alpha = ensureRole(
                guild, config.alphaRoleId(), ALPHA_NAME, new Color(0xC62828), true, logger);

        Map<GlyphTitle, Long> titles = new EnumMap<>(GlyphTitle.class);
        for (GlyphTitle title : GlyphTitle.values()) {
            long configured = config.titleRoleIds().getOrDefault(title, 0L);
            long roleId = ensureRole(
                    guild,
                    configured,
                    title.displayName(),
                    colorFor(title),
                    true,
                    logger);
            titles.put(title, roleId);
        }

        DiscordBotConfig resolved = config.withRoles(verified, alpha, tiers, titles);
        logger.info(
                "Discord roles ready — Verified={} Alpha={} tiers={} titles={}",
                resolved.verifiedRoleId(),
                resolved.alphaRoleId(),
                resolved.tierRoleIds(),
                resolved.titleRoleIds());
        return resolved;
    }

    private static long ensureRole(
            Guild guild,
            long configuredId,
            String name,
            Color color,
            boolean hoist,
            Logger logger) {
        if (configuredId != 0L) {
            Role existing = guild.getRoleById(configuredId);
            if (existing != null) {
                return configuredId;
            }
            logger.warn("Configured role id {} ({}) not found — will find/create by name",
                    configuredId, name);
        }

        List<Role> byName = guild.getRolesByName(name, false);
        if (!byName.isEmpty()) {
            long id = byName.getFirst().getIdLong();
            logger.info("Using existing Discord role '{}' ({})", name, id);
            return id;
        }

        try {
            Role created = guild.createRole()
                    .setName(name)
                    .setColor(color)
                    .setHoisted(hoist)
                    .setMentionable(false)
                    .reason("Glyph Discord bootstrap")
                    .complete();
            logger.info("Created Discord role '{}' ({})", name, created.getId());
            return created.getIdLong();
        } catch (InsufficientPermissionException | HierarchyException e) {
            throw new IllegalStateException(
                    "Cannot create role '" + name
                            + "'. Give the bot Manage Roles / Administrator and place its role "
                            + "above Glyph roles.", e);
        }
    }

    private static String displayName(DiscordTier tier) {
        return "Glyph " + tier.displayName();
    }

    private static Color colorFor(DiscordTier tier) {
        return switch (tier) {
            case INITIATE -> new Color(0x78909C);
            case SCOUT -> new Color(0x43A047);
            case BLOODED -> new Color(0xE53935);
            case VETERAN -> new Color(0xF9A825);
            case LEGEND -> new Color(0x26C6DA);
        };
    }

    private static Color colorFor(GlyphTitle title) {
        return switch (title) {
            case WANDERER -> new Color(0x90A4AE);
            case OUTLAW -> new Color(0xEF6C00);
            case WARLORD -> new Color(0x6A1B9A);
            case BLOODED -> new Color(0xB71C1C);
            case HUNTER -> new Color(0xF9A825);
            case BROKER -> new Color(0x00897B);
        };
    }
}
