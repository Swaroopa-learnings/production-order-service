package com.production.production_order_service.repository;

import com.production.production_order_service.dto.FactoryProductKey;
import com.production.production_order_service.dto.OrderStatus;
import com.production.production_order_service.entity.ProductionOrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Repository interface for managing ProductionOrderEntity entities.
 */
@Repository
public interface OrderRepository extends JpaRepository<ProductionOrderEntity, Long> {

    boolean existsByFactoryIdAndProductCode(Long factoryId, String productCode);

    Page<ProductionOrderEntity> findByFactoryId(Long factoryId, Pageable pageable);

    Page<ProductionOrderEntity> findByFactoryIdAndStatus(Long factoryId, OrderStatus status, Pageable pageable);

    Page<ProductionOrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    /**
     * Returns the natural keys that already exist for the given candidates. The IN/IN pair is a
     * superset of the (factoryId, productCode) combinations we care about, so the caller filters
     * the result on exact pairs; the point is to answer the whole batch in one indexed query
     * rather than one existence check per row.
     */
    @Query("""
           select new com.production.production_order_service.dto.FactoryProductKey(o.factoryId, o.productCode)
           from ProductionOrderEntity o
           where o.factoryId in :factoryIds
             and o.productCode in :productCodes
           """)
    List<FactoryProductKey> findExistingKeys(@Param("factoryIds") Collection<Long> factoryIds,
                                             @Param("productCodes") Collection<String> productCodes);
}
