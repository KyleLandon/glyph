package com.glyph.core.nick;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknameNamesTest {

    @Test
    void acceptsSimpleAndSpacedNames() {
        assertThat(NicknameNames.normalize("Rose")).contains("Rose");
        assertThat(NicknameNames.normalize("Lady Rose")).contains("Lady Rose");
        assertThat(NicknameNames.normalize("  Lady   Rose  ")).contains("Lady Rose");
        assertThat(NicknameNames.normalize("Ada_2")).contains("Ada_2");
    }

    @Test
    void rejectsShortLongAndSymbols() {
        assertThat(NicknameNames.normalize("A")).isEmpty();
        assertThat(NicknameNames.normalize("thisnameiswaytoolong")).isEmpty();
        assertThat(NicknameNames.normalize("Rose!")).isEmpty();
        assertThat(NicknameNames.normalize("")).isEmpty();
    }

    @Test
    void reservedWordsAreClearTokensNotNames() {
        assertThat(NicknameNames.isClearToken("off")).isTrue();
        assertThat(NicknameNames.isClearToken("RESET")).isTrue();
        assertThat(NicknameNames.normalize("off")).isEmpty();
    }
}
