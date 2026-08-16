package com.glyph.core.nick;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class NicknameServiceTest {

    private final UUID kyle = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID rose = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void setThenVisibleName() {
        NicknameService service = service(new InMemoryNicks());
        assertThat(service.set(kyle, "Lady Rose")).isEqualTo(NicknameService.SetStatus.SAVED);
        assertThat(service.visibleName(kyle, "KyleLandon")).isEqualTo("Lady Rose");
    }

    @Test
    void rejectsTakenNickname() {
        InMemoryNicks repo = new InMemoryNicks();
        NicknameService service = service(repo);
        assertThat(service.set(kyle, "Rose")).isEqualTo(NicknameService.SetStatus.SAVED);
        assertThat(service.set(rose, "rose")).isEqualTo(NicknameService.SetStatus.TAKEN);
    }

    @Test
    void rejectsOnlineUsername() {
        NicknameService service = new NicknameService(
                new InMemoryNicks(),
                () -> true,
                name -> name.equalsIgnoreCase("KyleLandon") ? Optional.of(kyle) : Optional.empty(),
                LoggerFactory.getLogger("test"));
        assertThat(service.set(rose, "KyleLandon")).isEqualTo(NicknameService.SetStatus.TAKEN);
        assertThat(service.set(kyle, "KyleLandon")).isEqualTo(NicknameService.SetStatus.SAVED);
    }

    @Test
    void clearRemovesNick() {
        NicknameService service = service(new InMemoryNicks());
        service.set(kyle, "Rose");
        assertThat(service.clear(kyle)).isEqualTo(NicknameService.ClearStatus.CLEARED);
        assertThat(service.visibleName(kyle, "KyleLandon")).isEqualTo("KyleLandon");
    }

    private static NicknameService service(NicknameRepository repo) {
        return new NicknameService(
                repo, () -> true, name -> Optional.empty(), LoggerFactory.getLogger("test"));
    }

    private static final class InMemoryNicks implements NicknameRepository {
        private final Map<UUID, String> byPlayer = new HashMap<>();

        @Override
        public Optional<String> find(UUID playerUuid) {
            return Optional.ofNullable(byPlayer.get(playerUuid));
        }

        @Override
        public Optional<UUID> findOwner(String nickname) {
            String key = nickname.toLowerCase(Locale.ROOT);
            return byPlayer.entrySet().stream()
                    .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).equals(key))
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        @Override
        public void upsert(UUID playerUuid, String nickname) {
            byPlayer.put(playerUuid, nickname);
        }

        @Override
        public boolean delete(UUID playerUuid) {
            return byPlayer.remove(playerUuid) != null;
        }
    }
}
