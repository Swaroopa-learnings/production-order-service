package com.production.production_order_service.exception;

public class ImportException extends RuntimeException{
    public ImportException(String message, Exception ex){
        super(message,ex);
    }
}
