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

@Repository
public interface BillRepository extends JpaRepository<Bill, String> {

    Optional<Bill> findByUniqueLink(String uniqueLink);

    List<Bill> findByStatus(Bill.BillStatus status);

    List<Bill> findByCustomerId(String customerId);

    List<Bill> findByCustomerIdAndStatus(String customerId, Bill.BillStatus status);

    @Query("SELECT b FROM Bill b WHERE " +
           "LOWER(b.id) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(b.customerName) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Bill> searchByIdOrCustomerName(@Param("term") String term);

    @Query("SELECT b FROM Bill b WHERE " +
           "(LOWER(b.id) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(b.customerName) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "AND b.status = :status")
    List<Bill> searchByIdOrCustomerNameAndStatus(
            @Param("term") String term,
            @Param("status") Bill.BillStatus status);

    @Query("SELECT b FROM Bill b WHERE b.createdAt BETWEEN :from AND :to")
    List<Bill> findByDateRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(b.total), 0) FROM Bill b WHERE b.status = 'PAID'")
    BigDecimal getTotalRevenue();

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.status = :status")
    long countByStatus(@Param("status") Bill.BillStatus status);

    @Query("SELECT COALESCE(SUM(b.total), 0) FROM Bill b " +
           "WHERE b.status = 'PAID' AND YEAR(b.createdAt) = :year")
    BigDecimal getTotalRevenueByYear(@Param("year") int year);

    @Query("SELECT MONTH(b.createdAt), COALESCE(SUM(b.total), 0) FROM Bill b " +
           "WHERE b.status = 'PAID' AND YEAR(b.createdAt) = :year " +
           "GROUP BY MONTH(b.createdAt) ORDER BY MONTH(b.createdAt)")
    List<Object[]> getMonthlyRevenue(@Param("year") int year);

    @Query("SELECT p.method, COUNT(p), COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.status = 'SUCCESS' GROUP BY p.method")
    List<Object[]> getPaymentMethodBreakdown();

    @Query("SELECT b FROM Bill b WHERE b.status = 'PENDING' AND b.dueDate < :today")
    List<Bill> findOverdueBills(@Param("today") LocalDate today);

    @Query("SELECT b FROM Bill b WHERE b.status = 'PENDING' " +
           "AND b.dueDate BETWEEN :today AND :sevenDaysLater")
    List<Bill> findBillsDueSoon(
            @Param("today") LocalDate today,
            @Param("sevenDaysLater") LocalDate sevenDaysLater);

    boolean existsByUniqueLink(String uniqueLink);
}
