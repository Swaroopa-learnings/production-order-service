package com.production.production_order_service.exception;

import java.time.Instant;

public record ErrorResponse(Instant timestamp,int status, String error, String message,String path) {
}
