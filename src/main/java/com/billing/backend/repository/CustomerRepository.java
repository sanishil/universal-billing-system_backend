package com.billing.backend.repository;

import com.billing.backend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * CustomerRepository — all database queries for the "customers" table.
 *
 * Key features:
 *  - Filtering by status (ACTIVE / INACTIVE)
 *  - Search by name or email (case-insensitive)
 *  - ID counter for generating CUST-001, CUST-002 format IDs
 *
 * @Query: for complex queries that can't be expressed as method names,
 *         we write JPQL (Java Persistence Query Language) — like SQL
 *         but uses entity/field names instead of table/column names.
 *
 *         Example: "c.name" refers to Customer.name field,
 *                  not the "name" column in the database.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {

    // ── Filter by Status ──────────────────────────────────────────────────────
    // SQL: SELECT * FROM customers WHERE status = ?
    List<Customer> findByStatus(Customer.CustomerStatus status);

    // ── Search: name OR email contains search term ────────────────────────────
    // %:term% → LIKE '%searchterm%' (contains, case-insensitive)
    // This powers the frontend search box on the customer list page
    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Customer> searchByNameOrEmail(@Param("term") String term);

    // ── Search with Status Filter ─────────────────────────────────────────────
    @Query("SELECT c FROM Customer c WHERE " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "AND c.status = :status")
    List<Customer> searchByNameOrEmailAndStatus(
            @Param("term") String term,
            @Param("status") Customer.CustomerStatus status);

    // ── Count for ID Generation ───────────────────────────────────────────────
    // Used to generate CUST-001, CUST-002 etc.
    // Returns the total number of customers ever created
    // SQL: SELECT COUNT(*) FROM customers
    long count();

    // ── Check for Duplicate Email ─────────────────────────────────────────────
    // Used when creating a customer to avoid duplicates
    boolean existsByEmail(String email);
}
