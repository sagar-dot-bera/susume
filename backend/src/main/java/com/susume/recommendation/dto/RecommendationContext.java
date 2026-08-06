package com.susume.recommendation.dto;

import com.susume.recommendation.entity.Item;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationContext {
    private UUID tenantId;
    private UUID externalUserId;
    private String externalItemId;
    private int limit;
    private Map<String, Object> filters;
    private Map<String, Object> tenantConfig;
    private Map<String, Object> stateData;

    public int getEffectiveLimit(int defaultLimit) {
        return limit > 0 ? limit : defaultLimit;
    }
}
