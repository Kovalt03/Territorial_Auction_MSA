package com.territorial.auction.domain.item.dto;

public record PurchaseItemResponse(
        Long itemId, String itemType, int purchased, int totalOwned, int costAP, int remainingAP) {}
