package com.territorial.map.internal.admin.dto;

import java.util.List;

public record AdminUserTerritoryPage(List<AdminUserTerritoryView> content, long totalElements) {}
