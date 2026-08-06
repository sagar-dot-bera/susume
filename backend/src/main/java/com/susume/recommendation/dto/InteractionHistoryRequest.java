package com.susume.recommendation.dto;

import java.time.Instant;
import java.util.UUID;

public record InteractionHistoryRequest(
        UUID tenantId,
        String externalUserId,
        int limit,
        Instant since,
        int pageNumber,
        String interactionType) {

}
