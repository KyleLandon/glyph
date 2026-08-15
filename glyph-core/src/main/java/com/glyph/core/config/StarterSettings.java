package com.glyph.core.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * First-join starter pack. Items are granted once per playerdata (so a world
 * wipe re-grants tools; economy first-join cash does not).
 */
public record StarterSettings(boolean enabled, List<StarterItem> items) {

    public StarterSettings {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public static List<StarterItem> defaults() {
        return List.of(
                new StarterItem(Material.STONE_SWORD, 1),
                new StarterItem(Material.STONE_PICKAXE, 1),
                new StarterItem(Material.STONE_AXE, 1),
                new StarterItem(Material.STONE_SHOVEL, 1),
                new StarterItem(Material.BREAD, 16),
                new StarterItem(Material.TORCH, 16));
    }

    /**
     * Parses {@code STONE_SWORD} or {@code bread:16}. Unknown materials fail
     * loudly at config load.
     */
    public static StarterItem parseItem(String spec) {
        String trimmed = Objects.requireNonNull(spec, "spec").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("starter.items entry is blank");
        }
        int colon = trimmed.indexOf(':');
        String name = colon < 0 ? trimmed : trimmed.substring(0, colon).trim();
        String amountPart = colon < 0 ? "1" : trimmed.substring(colon + 1).trim();
        String key = name.toUpperCase(Locale.ROOT);
        if (key.startsWith("MINECRAFT:")) {
            key = key.substring("MINECRAFT:".length());
        }
        Material material;
        try {
            material = Material.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown starter item material: " + spec, e);
        }
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            throw new IllegalArgumentException("Unknown starter item material: " + spec);
        }
        int amount;
        try {
            amount = Integer.parseInt(amountPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid starter item amount: " + spec, e);
        }
        if (amount < 1) {
            throw new IllegalArgumentException("Starter item amount must be >= 1: " + spec);
        }
        return new StarterItem(material, amount);
    }

    public record StarterItem(Material material, int amount) {

        public StarterItem {
            Objects.requireNonNull(material, "material");
            if (amount < 1) {
                throw new IllegalArgumentException("amount must be >= 1");
            }
        }

        public ItemStack stack() {
            int capped = Math.min(amount, material.getMaxStackSize());
            return new ItemStack(material, capped);
        }
    }
}
