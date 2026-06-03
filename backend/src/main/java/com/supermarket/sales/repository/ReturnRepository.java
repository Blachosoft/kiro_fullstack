package com.supermarket.sales.repository;

import com.supermarket.sales.domain.Return;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRepository extends JpaRepository<Return, Long> {

    List<Return> findByOriginalSaleId(Long originalSaleId);
}
