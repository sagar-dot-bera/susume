package com.susume.recommendation.dto;

import java.util.List;

public record InteractionExportResponse(
        List<InteractionExportItemResponse> data,
        int page,
        int limit,
        long totalElements,
        int totalPages) {
}
