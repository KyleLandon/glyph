package com.glyph.core.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class HomeServiceTest {

    private final UUID player = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void setRejectsWhenAtLimitUnlessReplacing() {
        InMemoryHomes repo = new InMemoryHomes();
        HomeService service = new HomeService(repo, () -> true, LoggerFactory.getLogger("test"));
        for (int i = 0; i < HomeNames.MAX_HOMES; i++) {
            assertThat(service.set(player, "h" + i, "world", 1, 70, 2, 90f, 0f))
                    .isEqualTo(HomeService.SetStatus.SAVED);
        }
        assertThat(service.set(player, "extra", "world", 1, 70, 2, 90f, 0f))
                .isEqualTo(HomeService.SetStatus.LIMIT);
        assertThat(service.set(player, "h0", "world", 3, 70, 4, 90f, 0f))
                .isEqualTo(HomeService.SetStatus.RENAMED_EXISTING);
    }

    @Test
    void deleteMissingIsMissing() {
        HomeService service = new HomeService(
                new InMemoryHomes(), () -> true, LoggerFactory.getLogger("test"));
        assertThat(service.delete(player, "home")).isEqualTo(HomeService.DeleteStatus.MISSING);
    }

    private static final class InMemoryHomes implements HomeRepository {
        private final Map<String, Home> byName = new HashMap<>();

        @Override
        public List<Home> list(UUID playerUuid) {
            return List.copyOf(byName.values());
        }

        @Override
        public Optional<Home> find(UUID playerUuid, String name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public int count(UUID playerUuid) {
            return byName.size();
        }

        @Override
        public void upsert(Home home) {
            byName.put(home.name(), home);
        }

        @Override
        public boolean delete(UUID playerUuid, String name) {
            return byName.remove(name) != null;
        }
    }
}
