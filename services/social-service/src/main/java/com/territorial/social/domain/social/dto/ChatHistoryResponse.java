package com.territorial.social.domain.social.dto;

import java.util.List;

public record ChatHistoryResponse(List<ChatMessageResponse> messages, boolean hasNext) {}
