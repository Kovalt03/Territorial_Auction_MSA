package com.territorial.auction.domain.social.dto;

import java.util.List;

public record ChatHistoryResponse(List<ChatMessageResponse> messages, boolean hasNext) {}
