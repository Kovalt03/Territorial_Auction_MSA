package com.territorial.map.internal.dto;

import java.util.List;

public record OwnerHoldingPage(List<OwnerHoldingView> content, long totalElements) {}
