package com.territorial.auction.domain.item.repository;

import com.territorial.auction.domain.item.entity.Item;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByItemType(Item.ItemType itemType);
}
