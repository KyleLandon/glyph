package com.glyph.core.smp.warp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarpRepository {

    List<PlayerWarp> listAll();

    List<PlayerWarp> listOwned(UUID ownerUuid);

    Optional<PlayerWarp> find(String name);

    int countOwned(UUID ownerUuid);

    void insert(PlayerWarp warp);

    boolean delete(String name, UUID ownerUuid);
}
