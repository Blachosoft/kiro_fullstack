package com.supermarket.sales.repository;

import com.supermarket.sales.domain.Sale;
import com.supermarket.sales.domain.SaleState;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByTerminalIdAndStatusOrderByFrozenAtDesc(String terminalId, SaleState status);

    List<Sale> findByStatusAndFrozenAtBefore(SaleState status, LocalDateTime frozenAt);
}
