package com.glyph.discord.staff;

import java.util.Objects;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;

/** `/staffhelp` ephemeral menu + `/staffguide setup` forum publisher. */
public final class StaffHelpListener extends ListenerAdapter {

    public static final String SELECT_ID = "staffhelp:topic";

    private final StaffGuideService guide;
    private final Logger logger;

    public StaffHelpListener(StaffGuideService guide, Logger logger) {
        this.guide = Objects.requireNonNull(guide, "guide");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "staffhelp" -> handleStaffHelp(event);
            case "staffguide" -> handleStaffGuide(event);
            default -> {
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!SELECT_ID.equals(event.getComponentId())) {
            return;
        }
        if (!StaffGuideService.isStaff(event.getMember())) {
            event.reply("Staff only.").setEphemeral(true).queue();
            return;
        }
        if (event.getValues().isEmpty()) {
            event.deferEdit().queue();
            return;
        }
        StaffGuideTopic.fromId(event.getValues().getFirst()).ifPresentOrElse(
                topic -> event.editMessageEmbeds(StaffGuideEmbeds.embed(topic))
                        .setComponents(ActionRow.of(topicMenu()))
                        .queue(),
                () -> event.reply("Unknown topic.").setEphemeral(true).queue());
    }

    private void handleStaffHelp(SlashCommandInteractionEvent event) {
        if (!StaffGuideService.isStaff(event.getMember())) {
            event.reply("Staff only — need Manage Server / Manage Roles.").setEphemeral(true).queue();
            return;
        }
        String topicOpt = event.getOption("topic") != null
                ? event.getOption("topic").getAsString()
                : null;
        if (topicOpt != null) {
            StaffGuideTopic.fromId(topicOpt).ifPresentOrElse(
                    topic -> event.replyEmbeds(StaffGuideEmbeds.embed(topic))
                            .addComponents(ActionRow.of(topicMenu()))
                            .setEphemeral(true)
                            .queue(),
                    () -> event.reply("Unknown topic. Use the menu.").setEphemeral(true).queue());
            return;
        }
        event.replyEmbeds(StaffGuideEmbeds.indexEmbed())
                .addComponents(ActionRow.of(topicMenu()))
                .setEphemeral(true)
                .queue();
    }

    private void handleStaffGuide(SlashCommandInteractionEvent event) {
        if (!StaffGuideService.isStaff(event.getMember())) {
            event.reply("Staff only — need Manage Server / Manage Roles.").setEphemeral(true).queue();
            return;
        }
        String sub = event.getSubcommandName();
        if (!"setup".equals(sub)) {
            event.reply("Usage: `/staffguide setup`").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        try {
            StaffGuideService.SetupResult result = guide.setup(event.getGuild());
            event.getHook().sendMessage(
                    "Staff guide published in " + result.mention()
                            + "\n" + result.jumpUrl()
                            + "\nSuggested pin for staff-chat:\n"
                            + "> **Glyph Staff Guide**\n"
                            + "> Use " + result.mention() + " for the full reference.\n"
                            + "> Quick: `/staffhelp` · `/glyph status` · `/eco get <player>` · `/stats <player>`")
                    .queue();
        } catch (Exception e) {
            logger.error("staffguide setup failed", e);
            event.getHook().sendMessage(
                    "Setup failed: " + e.getMessage()
                            + "\nEnsure the bot can **Manage Channels** and post in forums.").queue();
        }
    }

    static StringSelectMenu topicMenu() {
        StringSelectMenu.Builder menu = StringSelectMenu.create(SELECT_ID)
                .setPlaceholder("Pick a staff guide topic…")
                .setRequiredRange(1, 1);
        for (StaffGuideTopic topic : StaffGuideTopic.values()) {
            menu.addOption(topic.emoji() + " " + topic.title(), topic.id(), trim(topic.description(), 100));
        }
        return menu.build();
    }

    private static String trim(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
