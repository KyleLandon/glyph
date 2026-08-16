package com.glyph.core.smp.shop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChestShopRepository {

    Optional<ChestShop> findAt(String world, int x, int y, int z);

    List<ChestShop> listAll();

    void insert(ChestShop shop);

    boolean delete(UUID id, UUID ownerUuid);
}
