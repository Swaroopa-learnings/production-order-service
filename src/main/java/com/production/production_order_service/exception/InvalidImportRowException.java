package com.production.production_order_service.exception;

public class InvalidImportRowException extends RuntimeException{
    public InvalidImportRowException(String message){
        super(message);
    }
}
