package com.glyph.discord.commands;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Accepts plain chat {@code /link CODE} or {@code !link CODE} so linking works
 * even when slash commands have not propagated yet.
 */
public final class MessageLinkListener extends ListenerAdapter {

    private static final Pattern LINK_LINE = Pattern.compile(
            "^[/!]link\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);

    private final AccountLinkService links;

    public MessageLinkListener(AccountLinkService links) {
        this.links = Objects.requireNonNull(links, "links");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }
        String content = event.getMessage().getContentRaw().trim();
        Matcher matcher = LINK_LINE.matcher(content);
        if (!matcher.matches()) {
            return;
        }
        String code = matcher.group(1);
        AccountLinkService.LinkResult result = links.link(
                code,
                event.getAuthor().getIdLong(),
                event.getGuild(),
                event.getMember());

        MessageChannel channel = event.getChannel();
        channel.sendMessage(event.getAuthor().getAsMention() + " " + result.message())
                .queue(sent -> {
                    // Prefer DMs for success noise, but channel reply is fine for v1.
                });

        // Nudge users toward slash once available
        if (content.toLowerCase(Locale.ROOT).startsWith("/link") && !result.success()) {
            // no-op
        }
    }
}
