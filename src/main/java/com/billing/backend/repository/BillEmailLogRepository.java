package com.billing.backend.repository;

import com.billing.backend.entity.BillEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillEmailLogRepository extends JpaRepository<BillEmailLog, Long> {

    List<BillEmailLog> findByBillIdOrderByCreatedAtDesc(String billId);

    Optional<BillEmailLog> findFirstByBillIdOrderByCreatedAtDesc(String billId);

    void deleteByBillId(String billId);
}
