package com.susume.recommendation.dto;

import java.util.List;

public record InteractionHistoryResponse(
        List<InteractionHistoryItemResponse> data,
        int page,
        int limit,
        long totalElements,
        int totalPages) {
}
