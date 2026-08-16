package com.glyph.core.smp.warp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WarpServiceTest {

    private final UUID owner = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void createRejectsTakenNamesAndLimits() {
        InMemoryWarps repo = new InMemoryWarps();
        WarpService service = new WarpService(repo, () -> true, 2, LoggerFactory.getLogger("test"));
        assertThat(service.create(owner, "alpha", "world", 1, 70, 2, 0, 0))
                .isEqualTo(WarpService.SetStatus.CREATED);
        assertThat(service.create(owner, "alpha", "world", 3, 70, 4, 0, 0))
                .isEqualTo(WarpService.SetStatus.TAKEN);
        assertThat(service.create(owner, "beta", "world", 1, 70, 2, 0, 0))
                .isEqualTo(WarpService.SetStatus.CREATED);
        assertThat(service.create(owner, "gamma", "world", 1, 70, 2, 0, 0))
                .isEqualTo(WarpService.SetStatus.LIMIT);
    }

    @Test
    void deleteMissingIsMissing() {
        WarpService service = new WarpService(
                new InMemoryWarps(), () -> true, 3, LoggerFactory.getLogger("test"));
        assertThat(service.delete(owner, "gone")).isEqualTo(WarpService.DeleteStatus.MISSING);
    }

    private static final class InMemoryWarps implements WarpRepository {
        private final Map<String, PlayerWarp> byName = new HashMap<>();

        @Override
        public List<PlayerWarp> listAll() {
            return new ArrayList<>(byName.values());
        }

        @Override
        public List<PlayerWarp> listOwned(UUID ownerUuid) {
            return byName.values().stream().filter(w -> w.ownerUuid().equals(ownerUuid)).toList();
        }

        @Override
        public Optional<PlayerWarp> find(String name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public int countOwned(UUID ownerUuid) {
            return (int) byName.values().stream().filter(w -> w.ownerUuid().equals(ownerUuid)).count();
        }

        @Override
        public void insert(PlayerWarp warp) {
            byName.put(warp.name(), warp);
        }

        @Override
        public boolean delete(String name, UUID ownerUuid) {
            PlayerWarp existing = byName.get(name);
            if (existing == null || !existing.ownerUuid().equals(ownerUuid)) {
                return false;
            }
            byName.remove(name);
            return true;
        }
    }
}
