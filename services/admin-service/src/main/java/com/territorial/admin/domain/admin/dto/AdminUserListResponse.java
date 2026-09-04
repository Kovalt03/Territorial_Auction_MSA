package com.territorial.admin.domain.admin.dto;

import java.util.List;

public record AdminUserListResponse(
        long totalCount, int page, int size, List<AdminUserResponse> users) {}
