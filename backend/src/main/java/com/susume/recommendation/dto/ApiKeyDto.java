package com.susume.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyDto {
    private UUID id;
    private UUID tenantId;
    private String key;
    private Instant createdAt;
    private Instant updatedAt;
}
