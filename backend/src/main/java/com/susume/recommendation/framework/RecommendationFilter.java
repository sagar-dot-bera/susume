package com.susume.recommendation.framework;

import com.susume.recommendation.entity.Item;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RecommendationFilter {

    public List<Item> filterActiveAndTenant(List<Item> items, UUID tenantId) {
        if (items == null) return List.of();
        return items.stream()
                .filter(item -> tenantId.equals(item.getTenantId()))
                .filter(item -> "ACTIVE".equalsIgnoreCase(item.getStatus()))
                .collect(Collectors.toList());
    }

    public List<Item> excludeInteractedItems(List<Item> items, Set<String> interactedItemIds) {
        if (items == null) return List.of();
        if (interactedItemIds == null || interactedItemIds.isEmpty()) return items;
        return items.stream()
                .filter(item -> !interactedItemIds.contains(item.getExternalItemId()))
                .collect(Collectors.toList());
    }

    public List<Item> applyCustomFilters(List<Item> items, Map<String, Object> filters) {
        if (items == null) return List.of();
        if (filters == null || filters.isEmpty()) return items;

        return items.stream().filter(item -> {
            Map<String, Object> metadata = item.getMetadata();
            if (metadata == null) return false;

            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                String key = entry.getKey();
                Object requiredVal = entry.getValue();
                if (requiredVal == null) continue;

                Object actualVal = metadata.get(key);
                if (actualVal == null || !requiredVal.toString().equalsIgnoreCase(actualVal.toString())) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());
    }
}
