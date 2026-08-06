package com.susume.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@AllArgsConstructor
@Getter
public class DashboardStatsResponse {
    @JsonProperty("itemCount")
    public long itemCount;

    @JsonProperty("totalRecs")
    public long totalRecs;

    @JsonProperty("totalInteractions")
    public long totalInteractions;

    @JsonProperty("avgLatency")
    public int avgLatency;

    @JsonProperty("apiKeyCount")
    public int apiKeyCount;

    @JsonProperty("hitsOverTime")
    public Map<String, Long> hitsOverTime;

    @JsonProperty("typeBreakdown")
    public Map<String, Long> typeBreakdown;

}
