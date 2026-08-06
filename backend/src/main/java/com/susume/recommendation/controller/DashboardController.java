package com.susume.recommendation.controller;

import com.susume.recommendation.dto.DashboardStatsResponse;
import com.susume.recommendation.dto.TenantInfoResponse;
import com.susume.recommendation.entity.Tenant;
import com.susume.recommendation.filter.JwtContext;
import com.susume.recommendation.service.StatesService;
import com.susume.recommendation.service.TenantService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@Slf4j
public class DashboardController {

    private final TenantService tenantService;
    private final StatesService statesService;

    public DashboardController(TenantService tenantService, StatesService statesService) {
        this.tenantService = tenantService;
        this.statesService = statesService;
    }

    /**
     * Get details of the currently authenticated tenant.
     * GET /api/v1/dashboard/tenant
     */
    @GetMapping("/tenant")
    public ResponseEntity<TenantInfoResponse> getTenantDetails() {
        UUID tenantId = JwtContext.getTenantId();
        Tenant tenant = tenantService.fetchTenant(tenantId);
        log.info("Fetched tenant details for tenantId: {}", tenantId);

        return ResponseEntity.ok(tenantService.tenantToTenantInfoResponse(tenant));
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        UUID tenantId = JwtContext.getTenantId();
        log.info("Fetching dashboard stats for tenantId: {}", tenantId);
        DashboardStatsResponse stats = statesService.getDashboardStats(tenantId);
        log.info("Fetched dashboard stats for tenantId: {}", tenantId);
        return ResponseEntity.ok(stats);

    }

}
