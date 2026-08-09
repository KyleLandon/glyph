package com.glyph.api.economy;

import java.util.UUID;

/**
 * One row of the balance leaderboard ({@code /baltop}).
 */
public record TopBalance(UUID playerUuid, String username, Money balance) {
}
