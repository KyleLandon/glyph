package com.glyph.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuctionSettingsTest {

    @Test
    void feesRoundUpAndNeverGoNegative() {
        // 1% of $5,000 = $50.
        assertThat(AuctionSettings.fee(5000, 100)).isEqualTo(50);
        // 1% of $1 rounds up to $1 — fees are never free.
        assertThat(AuctionSettings.fee(1, 100)).isEqualTo(1);
        // 5% of $1,000 = $50.
        assertThat(AuctionSettings.fee(1000, 500)).isEqualTo(50);
        // Zero rate charges nothing.
        assertThat(AuctionSettings.fee(5000, 0)).isZero();
        assertThat(AuctionSettings.fee(0, 500)).isZero();
    }

    @Test
    void helpersUseConfiguredRates() {
        AuctionSettings settings = new AuctionSettings(true, 100, 500, 10, 48);
        // 1% listing / 5% sale fee on a $10,000 asking price.
        assertThat(settings.listingFee(10_000)).isEqualTo(100);
        assertThat(settings.saleFee(10_000)).isEqualTo(500);
    }
}
