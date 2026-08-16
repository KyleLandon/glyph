package com.glyph.core.nick;

import java.util.Optional;
import java.util.UUID;

public interface NicknameRepository {

    Optional<String> find(UUID playerUuid);

    Optional<UUID> findOwner(String nickname);

    void upsert(UUID playerUuid, String nickname);

    boolean delete(UUID playerUuid);
}
