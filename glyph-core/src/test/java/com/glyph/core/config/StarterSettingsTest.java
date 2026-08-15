package com.glyph.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class StarterSettingsTest {

    @Test
    void parseItemAcceptsBareMaterial() {
        StarterSettings.StarterItem item = StarterSettings.parseItem("stone_sword");
        assertThat(item.material()).isEqualTo(Material.STONE_SWORD);
        assertThat(item.amount()).isEqualTo(1);
    }

    @Test
    void parseItemAcceptsAmount() {
        StarterSettings.StarterItem item = StarterSettings.parseItem("BREAD:16");
        assertThat(item.material()).isEqualTo(Material.BREAD);
        assertThat(item.amount()).isEqualTo(16);
    }

    @Test
    void parseItemRejectsUnknownMaterial() {
        assertThatThrownBy(() -> StarterSettings.parseItem("not_an_item"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown starter item");
    }

    @Test
    void parseItemRejectsZeroAmount() {
        assertThatThrownBy(() -> StarterSettings.parseItem("torch:0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 1");
    }

    @Test
    void defaultsAreStoneToolsPlusFoodAndTorches() {
        assertThat(StarterSettings.defaults())
                .extracting(StarterSettings.StarterItem::material)
                .containsExactly(
                        Material.STONE_SWORD,
                        Material.STONE_PICKAXE,
                        Material.STONE_AXE,
                        Material.STONE_SHOVEL,
                        Material.BREAD,
                        Material.TORCH);
    }
}
