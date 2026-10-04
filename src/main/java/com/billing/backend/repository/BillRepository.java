package com.billing.backend.repository;

import com.billing.backend.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * BillRepository — all database queries for the "bills" table.
 *
 * Contains queries for:
 *  - Filtering bills by status, customer, date range
 *  - Searching by bill ID or customer name
 *  - Getting revenue statistics for the reports module
 *  - Finding bills by the public share link
 */
@Repository
public interface BillRepository extends JpaRepository<Bill, String> {

    // ── Find by Public Share Link ─────────────────────────────────────────────
    // Used for the public bill view page (no login required)
    // SQL: SELECT * FROM bills WHERE unique_link = ?
    Optional<Bill> findByUniqueLink(String uniqueLink);

    // ── Filter by Status ──────────────────────────────────────────────────────
    List<Bill> findByStatus(Bill.BillStatus status);

    // ── Filter by Customer ────────────────────────────────────────────────────
    List<Bill> findByCustomerId(String customerId);

    // ── Filter by Customer + Status ───────────────────────────────────────────
    List<Bill> findByCustomerIdAndStatus(String customerId, Bill.BillStatus status);

    // ── Search: bill ID OR customer name contains term ────────────────────────
    // This powers the search box on the bill list page
    // Frontend searches BOTH id AND customerName simultaneously
    @Query("SELECT b FROM Bill b WHERE " +
           "LOWER(b.id) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(b.customerName) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Bill> searchByIdOrCustomerName(@Param("term") String term);

    // ── Search with Status Filter ─────────────────────────────────────────────
    @Query("SELECT b FROM Bill b WHERE " +
           "(LOWER(b.id) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(b.customerName) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "AND b.status = :status")
    List<Bill> searchByIdOrCustomerNameAndStatus(
            @Param("term") String term,
            @Param("status") Bill.BillStatus status);

    // ── Date Range Filter ─────────────────────────────────────────────────────
    @Query("SELECT b FROM Bill b WHERE b.createdAt BETWEEN :from AND :to")
    List<Bill> findByDateRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // ── STATISTICS QUERIES (for Reports module) ───────────────────────────────

    // Total revenue = sum of all PAID bill totals
    // COALESCE: if no PAID bills exist, return 0 instead of null
    @Query("SELECT COALESCE(SUM(b.total), 0) FROM Bill b WHERE b.status = 'PAID'")
    BigDecimal getTotalRevenue();

    // Count bills by each status
    @Query("SELECT COUNT(b) FROM Bill b WHERE b.status = :status")
    long countByStatus(@Param("status") Bill.BillStatus status);

    // Total revenue for a specific year (Year-To-Date)
    @Query("SELECT COALESCE(SUM(b.total), 0) FROM Bill b " +
           "WHERE b.status = 'PAID' AND YEAR(b.createdAt) = :year")
    BigDecimal getTotalRevenueByYear(@Param("year") int year);

    // Monthly revenue breakdown — returns [ [month, amount], [month, amount], ... ]
    // MONTH() extracts month number from createdAt date
    @Query("SELECT MONTH(b.createdAt), COALESCE(SUM(b.total), 0) FROM Bill b " +
           "WHERE b.status = 'PAID' AND YEAR(b.createdAt) = :year " +
           "GROUP BY MONTH(b.createdAt) ORDER BY MONTH(b.createdAt)")
    List<Object[]> getMonthlyRevenue(@Param("year") int year);

    // Payment method breakdown — for the analytics pie chart
    // Joins with payments table to get method distribution
    @Query("SELECT p.method, COUNT(p), COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.status = 'SUCCESS' GROUP BY p.method")
    List<Object[]> getPaymentMethodBreakdown();

    // ── Find Overdue Bills ─────────────────────────────────────────────────────
    // Used for scheduled jobs to auto-mark bills as OVERDUE
    @Query("SELECT b FROM Bill b WHERE b.status = 'PENDING' AND b.dueDate < :today")
    List<Bill> findOverdueBills(@Param("today") LocalDate today);

    // ── Find Bills Due in 7 days (for reminder notifications) ─────────────────
    @Query("SELECT b FROM Bill b WHERE b.status = 'PENDING' " +
           "AND b.dueDate BETWEEN :today AND :sevenDaysLater")
    List<Bill> findBillsDueSoon(
            @Param("today") LocalDate today,
            @Param("sevenDaysLater") LocalDate sevenDaysLater);

    // ── Check if uniqueLink already exists ────────────────────────────────────
    boolean existsByUniqueLink(String uniqueLink);
}
