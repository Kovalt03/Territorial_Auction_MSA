package com.territorial.auction.domain.user.dto;

import com.territorial.auction.domain.user.entity.Wishlist;
import java.util.List;

public record WishlistResponse(List<Long> territoryIds) {

    public static WishlistResponse from(List<Wishlist> wishlists) {
        return new WishlistResponse(wishlists.stream().map(Wishlist::getTerritoryId).toList());
    }
}
