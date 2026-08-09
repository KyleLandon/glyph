package com.glyph.core.config;

/**
 * Active-playtime earnings (GDD section 16 faucet). Players are paid a
 * fixed amount for every interval in which they were demonstrably active —
 * AFK players earn nothing, and no payment is "massive passive daily".
 *
 * @param enabled         master switch
 * @param intervalMinutes payout window length
 * @param amount          payment per active window, in whole dollars
 * @param minActivity     activity required per window before it pays:
 *                        1 unit = one block broken/placed or one meter moved
 */
public record PlaytimeRewardSettings(
        boolean enabled,
        int intervalMinutes,
        long amount,
        int minActivity) {

    /** Internal activity is tracked in centimeter-equivalents. */
    public long minActivityUnits() {
        return minActivity * 100L;
    }
}
