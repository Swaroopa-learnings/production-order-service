package com.production.production_order_service.dto;

import java.util.List;

public record ImportResult(
        int totalRows,
        int importedRows,
        int failedRows,
        List<String> errors
) {
}
