package com.territorial.auction.domain.user.service;

import com.territorial.auction.domain.map.entity.Territory;
import com.territorial.auction.domain.map.repository.TerritoryRepository;
import com.territorial.auction.domain.user.dto.WishlistResponse;
import com.territorial.auction.domain.user.entity.User;
import com.territorial.auction.domain.user.entity.Wishlist;
import com.territorial.auction.domain.user.repository.UserRepository;
import com.territorial.auction.domain.user.repository.WishlistRepository;
import com.territorial.auction.global.exception.CustomException;
import com.territorial.auction.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final TerritoryRepository territoryRepository;
    private final UserRepository userRepository;

    public WishlistResponse getWishlist(Long userId) {
        return WishlistResponse.from(wishlistRepository.findAllByUserId(userId));
    }

    @Transactional
    public void addWishlist(Long userId, Long territoryId) {
        Territory territory =
                territoryRepository
                        .findById(territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.TERRITORY_NOT_FOUND));
        validateNotDuplicate(userId, territoryId);

        User user = userRepository.getReferenceById(userId);
        Wishlist wishlist = Wishlist.builder().user(user).territory(territory).build();
        wishlistRepository.save(wishlist);
    }

    @Transactional
    public void removeWishlist(Long userId, Long territoryId) {
        Wishlist wishlist =
                wishlistRepository
                        .findByUserIdAndTerritoryId(userId, territoryId)
                        .orElseThrow(() -> new CustomException(ErrorCode.WISHLIST_NOT_FOUND));
        wishlistRepository.delete(wishlist);
    }

    private void validateNotDuplicate(Long userId, Long territoryId) {
        if (wishlistRepository.existsByUserIdAndTerritoryId(userId, territoryId)) {
            throw new CustomException(ErrorCode.WISHLIST_ALREADY_EXISTS);
        }
    }
}
