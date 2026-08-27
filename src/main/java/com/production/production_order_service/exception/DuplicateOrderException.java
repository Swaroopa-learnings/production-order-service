package com.production.production_order_service.exception;

public class DuplicateOrderException extends RuntimeException{

    public DuplicateOrderException(String message){
        super(message);
    }
}
