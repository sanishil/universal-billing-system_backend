package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * BillItem entity — maps to the "bill_items" table.
 *
 * Each row is one line item in an invoice. For example:
 *   | Cloud Infrastructure | 998314 | qty: 2 | price: 50000 | amount: 100000 |
 *
 * @ManyToOne → many items belong to ONE bill
 * @JoinColumn → the FK column in bill_items table is "bill_id"
 */
@Entity
@Table(name = "bill_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillItem {

    // ── Primary Key ──────────────────────────────────────────────────────────
    // Format: item-{timestamp}-{index}, e.g. "item-1728000000000-0"
    @Id
    @Column(name = "id", length = 60)
    private String id;

    // ── Relationship: Many items → One Bill ───────────────────────────────────
    // @ManyToOne: this item belongs to one bill
    // @JoinColumn: the "bill_id" column in THIS table is the FK to bills.id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    // ── Item Details ─────────────────────────────────────────────────────────
    // The service/product name, e.g. "Cloud Infrastructure & Security"
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    // HSN = Harmonized System of Nomenclature (for goods)
    // SAC = Services Accounting Code (for services)
    // Default "998314" = IT design and development services
    @Column(name = "hsn_sac", length = 10)
    @Builder.Default
    private String hsnSac = "998314";

    // How many units (can be decimal, e.g. 1.5 hours)
    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    // Price per unit in INR
    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    // amount = quantity × price (computed on server, stored for quick display)
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
}
