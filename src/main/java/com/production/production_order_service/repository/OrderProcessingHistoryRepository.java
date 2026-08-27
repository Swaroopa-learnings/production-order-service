package com.production.production_order_service.repository;

import com.production.production_order_service.dto.OrderStatus;
import com.production.production_order_service.entity.OrderProcessingHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for managing OrderProcessingHistoryEntity entities.
 */
@Repository
public interface OrderProcessingHistoryRepository extends JpaRepository<OrderProcessingHistoryEntity, Long> {

    /**
     * An order may legitimately enter the same status more than once, so this must not assume a
     * single row exists. Deriving "first, ordered by timestamp" keeps the Optional return type
     * without risking IncorrectResultSizeDataAccessException.
     */
    Optional<OrderProcessingHistoryEntity> findFirstByOrderIdAndToStatusOrderByTimestampDesc(
            Long orderId, OrderStatus toStatus);
}
