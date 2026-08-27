package com.production.production_order_service.dto;

import jakarta.validation.constraints.*;

public record ProductionOrderRequest(
        @NotNull Long factoryId,
        @NotBlank String productCode,
        @NotNull @Positive Integer quantity,
        @NotNull @Min(1) @Max(10) Integer priority)
{ }
