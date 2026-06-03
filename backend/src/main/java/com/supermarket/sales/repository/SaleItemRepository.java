package com.supermarket.sales.repository;

import com.supermarket.sales.domain.SaleItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    Optional<SaleItem> findBySaleIdAndId(Long saleId, Long id);
}
