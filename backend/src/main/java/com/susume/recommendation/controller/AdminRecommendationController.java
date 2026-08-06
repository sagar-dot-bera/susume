package com.susume.recommendation.controller;

import com.susume.recommendation.dto.RecommendationContext;
import com.susume.recommendation.dto.RecommendationResponse;
import com.susume.recommendation.dto.StrategyInfoResponse;
import com.susume.recommendation.filter.TenantContext;
import com.susume.recommendation.framework.RecommendationStrategyResolver;
import com.susume.recommendation.service.RecommendationService;
import com.susume.recommendation.service.RecommendationStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/recommendations")
public class AdminRecommendationController {

    private final RecommendationStrategyResolver strategyResolver;
    private final RecommendationService recommendationService;
    private final Set<String> disabledStrategies = Collections.synchronizedSet(new HashSet<>());

    public AdminRecommendationController(RecommendationStrategyResolver strategyResolver,
                                         RecommendationService recommendationService) {
        this.strategyResolver = strategyResolver;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/strategies")
    public ResponseEntity<List<StrategyInfoResponse>> listStrategies() {
        List<RecommendationStrategy> strategies = strategyResolver.getAllStrategies();
        List<StrategyInfoResponse> response = strategies.stream()
                .map(s -> StrategyInfoResponse.builder()
                        .name(s.getName())
                        .algorithm(s.getAlgorithm().name())
                        .description("Strategy for " + s.getName())
                        .enabled(!disabledStrategies.contains(s.getName().toLowerCase()))
                        .build())
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/strategies/{strategyName}/enable")
    public ResponseEntity<Map<String, Object>> enableStrategy(@PathVariable String strategyName) {
        disabledStrategies.remove(strategyName.toLowerCase());
        return ResponseEntity.ok(Map.of("strategy", strategyName, "enabled", true));
    }

    @PostMapping("/strategies/{strategyName}/disable")
    public ResponseEntity<Map<String, Object>> disableStrategy(@PathVariable String strategyName) {
        disabledStrategies.add(strategyName.toLowerCase());
        return ResponseEntity.ok(Map.of("strategy", strategyName, "enabled", false));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> config = new HashMap<>();
        config.put("defaultEngine", "HYBRID");
        config.put("activeTarget", "HYBRID_V2_STABLE");
        config.put("lastUpdate", "2H AGO");
        config.put("globalStatus", "OPTIMIZED");
        
        Map<String, Double> interactionWeights = new HashMap<>();
        interactionWeights.put("clickEvents", 0.15);
        interactionWeights.put("likeActions", 0.35);
        interactionWeights.put("purchaseData", 0.50);
        config.put("interactionWeights", interactionWeights);

        Map<String, Object> advancedParams = new HashMap<>();
        advancedParams.put("similarityThreshold", 0.82);
        advancedParams.put("neighborCount", 150);
        advancedParams.put("confidenceLimit", 95);
        advancedParams.put("recLimit", 50);
        config.put("advancedParameters", advancedParams);

        Map<String, Integer> hybridBlend = new HashMap<>();
        hybridBlend.put("cfWeight", 45);
        hybridBlend.put("contentWeight", 45);
        hybridBlend.put("trendWeight", 10);
        config.put("hybridBlend", hybridBlend);

        return ResponseEntity.ok(config);
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> configPayload) {
        log.info("Updating strategy config for tenant {}: {}", TenantContext.getTenantId(), configPayload);
        return ResponseEntity.ok(configPayload);
    }

    @PostMapping("/preview")
    public ResponseEntity<RecommendationResponse> previewRecommendation(
            @RequestParam String strategy,
            @RequestBody(required = false) RecommendationContext context) {
        UUID tenantId = TenantContext.getTenantId();
        if (context == null) {
            context = new RecommendationContext();
        }
        context.setTenantId(tenantId);
        RecommendationResponse response = recommendationService.getRecommendationsByStrategy(strategy, context);
        return ResponseEntity.ok(response);
    }
}
