package com.billing.backend.repository;

import com.billing.backend.entity.BillItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * BillItemRepository — queries for the "bill_items" table.
 *
 * In most cases, items are loaded automatically via the Bill entity
 * (because of @OneToMany with CascadeType.ALL).
 *
 * This repository is mainly used when we need to access items
 * independently of a bill (e.g., for line-item reporting).
 */
@Repository
public interface BillItemRepository extends JpaRepository<BillItem, String> {

    // Load all items for a specific bill
    // SQL: SELECT * FROM bill_items WHERE bill_id = ?
    List<BillItem> findByBillId(String billId);

    // Delete all items when a bill is deleted
    // (CascadeType.ALL already handles this, but useful as fallback)
    void deleteByBillId(String billId);
}
