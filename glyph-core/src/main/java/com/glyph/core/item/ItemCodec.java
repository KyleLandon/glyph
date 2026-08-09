package com.glyph.core.item;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Serializes auction/delivery items (GDD section 61).
 *
 * <p>Item bytes use Paper's official {@link ItemStack#serializeAsBytes()},
 * which embeds the Minecraft data version and upgrades items through
 * DataFixerUpper on load — the "future-compatible metadata" requirement.
 * Our one-byte envelope version exists so the storage format itself can
 * evolve without guessing.</p>
 *
 * <p>The JSON summary is a denormalized copy for SQL browse/search/sort;
 * the serialized bytes remain the single source of truth for the item.</p>
 */
public final class ItemCodec {

    /** Envelope format version; bump if the byte layout ever changes. */
    private static final byte ENVELOPE_VERSION = 1;

    private static final Gson GSON = new Gson();

    private ItemCodec() {
    }

    /** Immutable snapshot of an {@link ItemStack} with a version envelope. */
    public static byte[] serialize(ItemStack item) {
        byte[] payload = item.serializeAsBytes();
        byte[] enveloped = new byte[payload.length + 1];
        enveloped[0] = ENVELOPE_VERSION;
        System.arraycopy(payload, 0, enveloped, 1, payload.length);
        return enveloped;
    }

    public static ItemStack deserialize(byte[] data) {
        if (data.length < 2 || data[0] != ENVELOPE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported item envelope version: " + (data.length == 0 ? "?" : data[0]));
        }
        byte[] payload = new byte[data.length - 1];
        System.arraycopy(data, 1, payload, 0, payload.length);
        return ItemStack.deserializeBytes(payload);
    }

    /** Browse metadata stored in {@code auction_listings.item_summary}. */
    public record ItemSummary(
            String material, int amount, String displayName, String category, String sellerName) {

        public static ItemSummary fromJson(String json) {
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            return new ItemSummary(
                    object.get("material").getAsString(),
                    object.get("amount").getAsInt(),
                    object.has("name") && !object.get("name").isJsonNull()
                            ? object.get("name").getAsString() : null,
                    object.get("category").getAsString(),
                    object.get("seller").getAsString());
        }

        public String toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("material", material);
            object.addProperty("amount", amount);
            object.addProperty("name", displayName);
            object.addProperty("category", category);
            object.addProperty("seller", sellerName);
            return GSON.toJson(object);
        }
    }

    public static ItemSummary summarize(ItemStack item, String sellerName) {
        Component customName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName() : null;
        String plainName = customName != null
                ? PlainTextComponentSerializer.plainText().serialize(customName) : null;
        return new ItemSummary(
                item.getType().name(),
                item.getAmount(),
                plainName,
                categorize(item.getType()).name(),
                sellerName);
    }

    /** Coarse browse categories (GDD section 21 "item categories"). */
    public enum Category {
        WEAPONS, TOOLS, ARMOR, FOOD, BLOCKS, MISC;

        public String displayName() {
            String lower = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }

    public static Category categorize(Material material) {
        String name = material.name();
        if (name.endsWith("_SWORD") || name.equals("BOW") || name.equals("CROSSBOW")
                || name.equals("TRIDENT") || name.equals("MACE")) {
            return Category.WEAPONS;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                || name.endsWith("_AXE") || name.equals("SHEARS") || name.equals("FLINT_AND_STEEL")
                || name.equals("FISHING_ROD")) {
            return Category.TOOLS;
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || name.equals("SHIELD") || name.equals("ELYTRA")) {
            return Category.ARMOR;
        }
        if (material.isEdible()) {
            return Category.FOOD;
        }
        if (material.isBlock()) {
            return Category.BLOCKS;
        }
        return Category.MISC;
    }
}
