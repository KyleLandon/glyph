package com.glyph.core.home;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeRepository {

    List<Home> list(UUID playerUuid);

    Optional<Home> find(UUID playerUuid, String name);

    int count(UUID playerUuid);

    void upsert(Home home);

    boolean delete(UUID playerUuid, String name);
}
