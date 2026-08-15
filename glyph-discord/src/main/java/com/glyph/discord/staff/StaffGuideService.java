package com.glyph.discord.staff;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.slf4j.Logger;

/** Creates / refreshes the #staff-guide forum from embed content. */
public final class StaffGuideService {

    public static final String FORUM_NAME = "staff-guide";
    public static final String INDEX_POST = "📌 START HERE";

    private final Logger logger;

    public StaffGuideService(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static boolean isStaff(Member member) {
        if (member == null) {
            return false;
        }
        return member.hasPermission(Permission.MANAGE_SERVER)
                || member.hasPermission(Permission.ADMINISTRATOR)
                || member.hasPermission(Permission.MANAGE_ROLES);
    }

    public SetupResult setup(Guild guild) {
        ForumChannel forum = findForum(guild).orElseGet(() -> createForum(guild));
        ensurePost(forum, INDEX_POST, MessageCreateData.fromEmbeds(StaffGuideEmbeds.indexEmbed()));
        for (StaffGuideTopic topic : StaffGuideTopic.values()) {
            ensurePost(
                    forum,
                    topic.forumPostName(),
                    MessageCreateData.fromEmbeds(StaffGuideEmbeds.embed(topic)));
        }
        logger.info("Staff guide ready in #{}", forum.getName());
        return new SetupResult(forum.getAsMention(), forum.getJumpUrl());
    }

    private ForumChannel createForum(Guild guild) {
        logger.info("Creating forum channel #{}", FORUM_NAME);
        return guild.createForumChannel(FORUM_NAME)
                .setTopic("Glyph staff handbook — use /staffhelp for quick ephemeral lookup")
                .complete();
    }

    private Optional<ForumChannel> findForum(Guild guild) {
        List<ForumChannel> forums = guild.getForumChannelsByName(FORUM_NAME, true);
        if (!forums.isEmpty()) {
            return Optional.of(forums.getFirst());
        }
        for (GuildChannel channel : guild.getChannels()) {
            if (channel instanceof ForumChannel forum
                    && forum.getName().equalsIgnoreCase(FORUM_NAME)) {
                return Optional.of(forum);
            }
        }
        return Optional.empty();
    }

    private void ensurePost(ForumChannel forum, String postName, MessageCreateData createData) {
        Optional<ThreadChannel> existing = findPost(forum, postName);
        if (existing.isPresent()) {
            ThreadChannel thread = existing.get();
            Message starter = thread.retrieveStartMessage().complete();
            starter.editMessage(MessageEditData.fromCreateData(createData)).queue(
                    ok -> logger.info("Updated staff guide post '{}'", postName),
                    err -> logger.warn("Failed updating '{}': {}", postName, err.toString()));
            return;
        }
        forum.createForumPost(postName, createData).complete();
        logger.info("Created staff guide post '{}'", postName);
    }

    private Optional<ThreadChannel> findPost(ForumChannel forum, String postName) {
        // Active + archived public threads that match the post title.
        List<ThreadChannel> active = forum.getThreadChannels();
        for (ThreadChannel thread : active) {
            if (thread.getName().equalsIgnoreCase(postName)) {
                return Optional.of(thread);
            }
        }
        try {
            for (ThreadChannel thread : forum.retrieveArchivedPublicThreadChannels().complete()) {
                if (thread.getName().equalsIgnoreCase(postName)) {
                    return Optional.of(thread);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not scan archived forum posts: {}", e.toString());
        }
        return Optional.empty();
    }

    public record SetupResult(String mention, String jumpUrl) {
    }
}
