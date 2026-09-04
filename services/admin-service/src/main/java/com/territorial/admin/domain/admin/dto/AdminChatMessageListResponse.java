package com.territorial.admin.domain.admin.dto;

import java.util.List;

public record AdminChatMessageListResponse(
        long totalCount, int page, int size, List<AdminChatMessageResponse> messages) {}
