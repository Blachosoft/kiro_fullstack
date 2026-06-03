package com.supermarket.sales.repository;

import com.supermarket.sales.domain.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    @Query("""
            select coalesce(sum(ri.quantity), 0)
            from ReturnItem ri
            where ri.returnRecord.originalSale.id = :saleId
              and ri.productId = :productId
            """)
    int sumReturnedQuantity(@Param("saleId") Long saleId, @Param("productId") Long productId);
}
