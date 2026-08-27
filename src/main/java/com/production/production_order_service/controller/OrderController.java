package com.production.production_order_service.controller;

import com.production.production_order_service.dto.ImportResult;
import com.production.production_order_service.dto.OrderStatus;
import com.production.production_order_service.dto.ProductionOrderRequest;
import com.production.production_order_service.dto.ProductionOrderResponse;
import com.production.production_order_service.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller class for handling production order related endpoints.
 * It has endpoints for creating, processing, retrieving and bulk uploading production orders.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService){
        this.orderService = orderService;
    }
    @PostMapping
    public ResponseEntity<ProductionOrderResponse> createOrder(@RequestBody @Valid ProductionOrderRequest request){
        ProductionOrderResponse response = orderService.createProductionOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<String> processOrder(@PathVariable Long id){
        orderService.processOrder(id);
        return ResponseEntity.status(HttpStatus.OK).body("Order Processing has started successfully");
    }

    @GetMapping
    public Page<ProductionOrderResponse> getOrders(
            @RequestParam(required = false) Long factoryId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
            ){
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("priority"), Sort.Order.desc("createdAt") )
        );

        return  orderService.getOrders(factoryId, status, pageable);

    }

    @PostMapping(value = "/import",
                consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult bulkUpload(@RequestParam("file")MultipartFile csvFile){
        return orderService.importOrders(csvFile);
    }
}
