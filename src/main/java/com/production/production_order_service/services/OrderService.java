package com.production.production_order_service.services;

import com.production.production_order_service.dto.FactoryProductKey;
import com.production.production_order_service.dto.ImportResult;
import com.production.production_order_service.dto.OrderStatus;
import com.production.production_order_service.dto.ProductionOrderRequest;
import com.production.production_order_service.dto.ProductionOrderResponse;
import com.production.production_order_service.entity.OrderProcessingHistoryEntity;
import com.production.production_order_service.entity.ProductionOrderEntity;
import com.production.production_order_service.exception.*;
import com.production.production_order_service.repository.OrderProcessingHistoryRepository;
import com.production.production_order_service.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Cap on how many per-row messages we return, so a garbage file cannot produce a huge response. */
    private static final int MAX_REPORTED_ERRORS = 100;

    private final OrderRepository orderRepository;
    private final OrderProcessingHistoryRepository processingHistoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${orders.import.batchsize}")
    private int BATCH_SIZE;

    public OrderService(OrderRepository orderRepository, OrderProcessingHistoryRepository processingHistoryRepository) {
        this.orderRepository = orderRepository;
        this.processingHistoryRepository = processingHistoryRepository;
    }


    @Transactional
    public ProductionOrderResponse createProductionOrder(ProductionOrderRequest request) {

        if (!orderRepository.existsByFactoryIdAndProductCode(request.factoryId(), request.productCode())) {
            ProductionOrderEntity savedRecord = orderRepository.save(toEntity(request));
            processingHistoryRepository.save(OrderProcessingHistoryEntity.builder()
                    .orderId(savedRecord.getId())
                    .toStatus(savedRecord.getStatus())
                    .timestamp(Instant.now())
                    .build());

            return toDto(savedRecord);
        }
        throw new DuplicateOrderException(
                "Order already exists for factoryId "
                        + request.factoryId()
                        + " and productCode "
                        + request.productCode()
        );
    }

    /**
     * @param orderId
     */
    @Transactional
    public void processOrder(Long orderId) {

        ProductionOrderEntity record = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id " + orderId));

        if (record.getStatus() != OrderStatus.CREATED) {
            throw new InvalidTransactionException(
                    "Order " + orderId + " cannot be processed from status " + record.getStatus()
            );
        }

        processingHistoryRepository
                .findFirstByOrderIdAndToStatusOrderByTimestampDesc(orderId, OrderStatus.CREATED)
                .orElseThrow(() -> new InvalidTransactionException(
                        "Processing history is inconsistent for order " + orderId
                ));

        record.setStatus(OrderStatus.PROCESSING);
        processingHistoryRepository.save(OrderProcessingHistoryEntity.builder()
                .orderId(orderId)
                .fromStatus(OrderStatus.CREATED)
                .toStatus(OrderStatus.PROCESSING)
                .timestamp(Instant.now())
                .build());
    }

    private ProductionOrderEntity toEntity(ProductionOrderRequest request) {
        return ProductionOrderEntity.builder()
                .factoryId(request.factoryId())
                .productCode(request.productCode())
                .quantity(request.quantity())
                .priority(request.priority())
                .status(OrderStatus.CREATED)
                .build();
    }

    private ProductionOrderResponse toDto(ProductionOrderEntity entity) {
        return new ProductionOrderResponse(entity.getId(),
                entity.getFactoryId(),
                entity.getProductCode(),
                entity.getStatus(),
                entity.getQuantity(),
                entity.getPriority(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }


    /**
     * Retrieves a paginated list of production orders, optionally filtered by factory ID and/or order status.
     *
     * @param factoryId the ID of the factory to filter by (optional)
     * @param status    the status of the orders to filter by (optional)
     * @param pageable  the pagination information
     * @return a page of production order responses
     */
    @Transactional(readOnly = true)
    public Page<ProductionOrderResponse> getOrders(Long factoryId, OrderStatus status, Pageable pageable) {
        Page<ProductionOrderEntity> result;
        if (factoryId != null && status != null) {
            result = orderRepository.findByFactoryIdAndStatus(factoryId, status, pageable);
        } else if (factoryId != null) {
            result = orderRepository.findByFactoryId(factoryId, pageable);
        } else if (status != null) {
            result = orderRepository.findByStatus(status, pageable);
        } else {
            result = orderRepository.findAll(pageable);
        }
        return result.map(this::toDto);
    }

    /**
     * Imports orders from a CSV upload with partial-success semantics: rows that cannot be parsed,
     * or that would collide with an order that already exists, are counted as failures and skipped
     * rather than aborting the whole file.
     * <p>
     * Rows are validated purely in memory before anything touches the persistence context, so a bad
     * row can never mark the transaction rollback-only. Rows that already exist in the database are
     * filtered out per batch with a single projection query, which keeps the unique constraint from
     * firing during flush.
     */
    @Transactional
    public ImportResult importOrders(MultipartFile file) {

        int totalRows = 0;
        int importedRows = 0;
        int failedRows = 0;
        List<String> errors = new ArrayList<>();

        List<ParsedRow> batch = new ArrayList<>(BATCH_SIZE);
        Set<FactoryProductKey> seenInFile = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine();
            log.info("Skipped CSV header row");

            String line;
            while ((line = reader.readLine()) != null) {
                totalRows++;

                ParsedRow row;
                try {
                    row = parseRow(line, totalRows);
                } catch (InvalidImportRowException ex) {
                    failedRows++;
                    addError(errors, ex.getMessage());
                    continue;
                }

                if (!seenInFile.add(row.key())) {
                    failedRows++;
                    addError(errors, "Row " + totalRows + ": duplicated earlier in the same file");
                    continue;
                }

                batch.add(row);

                if (batch.size() == BATCH_SIZE) {
                    BatchOutcome outcome = saveBatch(batch, errors);
                    importedRows += outcome.imported();
                    failedRows += outcome.failed();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                BatchOutcome outcome = saveBatch(batch, errors);
                importedRows += outcome.imported();
                failedRows += outcome.failed();
                batch.clear();
            }

        } catch (IOException ex) {
            throw new ImportException("Failed to read CSV file", ex);
        }

        log.info("Import finished: {} rows read, {} imported, {} failed", totalRows, importedRows, failedRows);
        return new ImportResult(totalRows, importedRows, failedRows, List.copyOf(errors));
    }

    private BatchOutcome saveBatch(List<ParsedRow> batch, List<String> errors) {

        Set<FactoryProductKey> alreadyPersisted = new HashSet<>(orderRepository.findExistingKeys(
                batch.stream().map(row -> row.key().factoryId()).collect(Collectors.toSet()),
                batch.stream().map(row -> row.key().productCode()).collect(Collectors.toSet())));

        List<ProductionOrderEntity> toSave = new ArrayList<>(batch.size());
        int failed = 0;

        for (ParsedRow row : batch) {
            if (alreadyPersisted.contains(row.key())) {
                failed++;
                addError(errors, "Row " + row.rowNumber() + ": order already exists for factoryId "
                        + row.key().factoryId() + " and productCode " + row.key().productCode());
            } else {
                toSave.add(row.toEntity());
            }
        }

        if (!toSave.isEmpty()) {
            orderRepository.saveAll(toSave);
            entityManager.flush();
            log.info("Persisted batch of {} orders", toSave.size());
        }
        entityManager.clear();

        return new BatchOutcome(toSave.size(), failed);
    }

    private ParsedRow parseRow(String line, int rowNumber) {

        String[] values = line.split(",", -1);
        if (values.length != 4) {
            throw new InvalidImportRowException(
                    "Row " + rowNumber + ": expected 4 columns but found " + values.length);
        }

        String factoryId = values[0].trim();
        String productCode = values[1].trim();
        String quantity = values[2].trim();
        String priority = values[3].trim();

        if (factoryId.isBlank() || productCode.isBlank() || quantity.isBlank() || priority.isBlank()) {
            throw new InvalidImportRowException("Row " + rowNumber + ": all four columns are required");
        }

        try {
            return new ParsedRow(
                    rowNumber,
                    new FactoryProductKey(Long.valueOf(factoryId), productCode),
                    Integer.valueOf(quantity),
                    Integer.valueOf(priority));
        } catch (NumberFormatException ex) {
            throw new InvalidImportRowException(
                    "Row " + rowNumber + ": factoryId, quantity and priority must be numeric");
        }
    }

    private void addError(List<String> errors, String message) {
        if (errors.size() < MAX_REPORTED_ERRORS) {
            errors.add(message);
        }
    }

    private record ParsedRow(int rowNumber, FactoryProductKey key, Integer quantity, Integer priority) {

        ProductionOrderEntity toEntity() {
            return ProductionOrderEntity.builder()
                    .factoryId(key.factoryId())
                    .productCode(key.productCode())
                    .quantity(quantity)
                    .priority(priority)
                    .status(OrderStatus.CREATED)
                    .build();
        }
    }

    private record BatchOutcome(int imported, int failed) {
    }
}
