package com.glyph.core.bounty;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.core.bounty.BountyRepository.TargetTotal;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class WantedPosterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void posterPagesReadLikeAWantedNotice() {
        String text = PLAIN.serialize(WantedPoster.posterPages("Steve", 25_000, 3, "$").getFirst());

        assertThat(WantedPoster.posterTitle("Steve")).isEqualTo("WANTED: Steve");
        assertThat(text).contains("WANTED");
        assertThat(text).contains("Steve");
        assertThat(text).contains("DEAD OR ALIVE");
        assertThat(text).contains("$25,000");
        assertThat(text).contains("3 open contracts");
    }

    @Test
    void emptyListExplainsHowToPlace() {
        String text = PLAIN.serialize(WantedPoster.listPages(List.of(), "$").getFirst());

        assertThat(text).contains("WANTED");
        assertThat(text).contains("No active bounties");
        assertThat(text).contains("/bounty add");
    }

    @Test
    void listPagesRankTargetsAndPaginate() {
        List<TargetTotal> targets = List.of(
                new TargetTotal(UUID.randomUUID(), "Kyle", 10_000, 2),
                new TargetTotal(UUID.randomUUID(), "Steve", 500, 1));
        String text = PLAIN.serialize(WantedPoster.listPages(targets, "$").getFirst());

        assertThat(WantedPoster.listPages(targets, "$")).hasSize(1);
        assertThat(text).contains("1. Kyle");
        assertThat(text).contains("$10,000");
        assertThat(text).contains("2. Steve");
        assertThat(text).contains("$500");
    }
}
