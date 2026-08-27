package com.production.production_order_service.dto;

/**
 * A record representing a composite key for a factory and product combination.
 */
public record FactoryProductKey(Long factoryId, String productCode) {
}
