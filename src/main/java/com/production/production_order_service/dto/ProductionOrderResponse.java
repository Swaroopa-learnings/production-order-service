package com.production.production_order_service.dto;

import java.time.Instant;


public record ProductionOrderResponse(
        Long id,
        Long factoryId,
        String productCode,
        OrderStatus status,
        Integer quantity,
        Integer priority,
        Instant createdAt,
        Instant updatedAt) {
}
