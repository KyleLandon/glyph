package com.glyph.core.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

/** Builds hoverable / click-to-copy item components for chat. */
public final class ItemChatFormatter {

    static final Pattern PLACEHOLDER = Pattern.compile("\\[i(?:tem)?]", Pattern.CASE_INSENSITIVE);

    private ItemChatFormatter() {
    }

    public static boolean containsPlaceholder(String plain) {
        return plain != null && PLACEHOLDER.matcher(plain).find();
    }

    /**
     * Replaces {@code [i]} / {@code [item]} in plain chat text with a hoverable
     * item component. Click copies a short summary to the clipboard.
     */
    public static Component replacePlaceholders(String plain, ItemStack hand) {
        Matcher matcher = PLACEHOLDER.matcher(plain);
        TextComponent.Builder builder = Component.text();
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                builder.append(Component.text(plain.substring(last, matcher.start())));
            }
            builder.append(itemComponent(hand));
            last = matcher.end();
        }
        if (last < plain.length()) {
            builder.append(Component.text(plain.substring(last)));
        }
        return builder.build();
    }

    public static Component itemComponent(ItemStack hand) {
        if (hand == null || hand.getType().isAir() || hand.getAmount() <= 0) {
            return Component.text("[empty hand]", NamedTextColor.DARK_GRAY);
        }

        String copyText = copySummary(hand);
        Component label = hand.displayName()
                .colorIfAbsent(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .hoverEvent(hand.asHoverEvent())
                .clickEvent(ClickEvent.copyToClipboard(copyText));

        TextComponent.Builder builder = Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(label);
        if (hand.getAmount() > 1) {
            builder.append(Component.text(" x" + hand.getAmount(), NamedTextColor.GRAY));
        }
        builder.append(Component.text("]", NamedTextColor.DARK_GRAY));
        return builder.build();
    }

    static String copySummary(ItemStack hand) {
        String key = hand.getType().getKey().asString();
        if (hand.getAmount() > 1) {
            return hand.getAmount() + "x " + key;
        }
        return key;
    }
}
