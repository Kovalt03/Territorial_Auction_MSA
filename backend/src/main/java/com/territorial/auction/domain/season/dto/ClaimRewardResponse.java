package com.territorial.auction.domain.season.dto;

import java.time.LocalDateTime;

public record ClaimRewardResponse(
        Long rewardId, String rewardName, String track, LocalDateTime claimedAt) {}
