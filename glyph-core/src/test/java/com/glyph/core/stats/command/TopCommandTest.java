package com.glyph.core.stats.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopCommandTest {

    @Test
    void parseCategoryAcceptsPrimaryNames() {
        assertThat(TopCommand.parseCategory("money")).contains(TopCommand.Category.MONEY);
        assertThat(TopCommand.parseCategory("KILLS")).contains(TopCommand.Category.KILLS);
        assertThat(TopCommand.parseCategory("deaths")).contains(TopCommand.Category.DEATHS);
        assertThat(TopCommand.parseCategory("playtime")).contains(TopCommand.Category.PLAYTIME);
        assertThat(TopCommand.parseCategory("bounty")).contains(TopCommand.Category.BOUNTY);
    }

    @Test
    void parseCategoryAcceptsAliases() {
        assertThat(TopCommand.parseCategory("baltop")).contains(TopCommand.Category.MONEY);
        assertThat(TopCommand.parseCategory("wanted")).contains(TopCommand.Category.BOUNTY);
        assertThat(TopCommand.parseCategory("time")).contains(TopCommand.Category.PLAYTIME);
    }

    @Test
    void parseCategoryRejectsUnknown() {
        assertThat(TopCommand.parseCategory("blocks")).isEmpty();
        assertThat(TopCommand.parseCategory("")).isEmpty();
        assertThat(TopCommand.parseCategory(null)).isEmpty();
    }
}
