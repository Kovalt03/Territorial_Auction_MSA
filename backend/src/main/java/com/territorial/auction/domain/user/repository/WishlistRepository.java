package com.territorial.auction.domain.user.repository;

import com.territorial.auction.domain.user.entity.Wishlist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    List<Wishlist> findAllByUserId(Long userId);

    Optional<Wishlist> findByUserIdAndTerritoryId(Long userId, Long territoryId);

    boolean existsByUserIdAndTerritoryId(Long userId, Long territoryId);
}
