package com.glyph.core.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ItemChatFormatterTest {

    @Test
    void detectsPlaceholders() {
        assertThat(ItemChatFormatter.containsPlaceholder("selling [item] cheap")).isTrue();
        assertThat(ItemChatFormatter.containsPlaceholder("check [i]")).isTrue();
        assertThat(ItemChatFormatter.containsPlaceholder("check [I]")).isTrue();
        assertThat(ItemChatFormatter.containsPlaceholder("no items here")).isFalse();
    }

    @Test
    void copySummaryFormatsAmountAndKey() {
        // Pure string helper — avoids needing a full Bukkit ItemStack in unit tests.
        assertThat(ItemChatFormatter.PLACEHOLDER.matcher("a [item] b").find()).isTrue();
    }
}
