package com.billing.backend.repository;

import com.billing.backend.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {

    List<Customer> findByStatus(Customer.CustomerStatus status);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByPhone(String phone);


    boolean existsByPhone(String phone);
    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Customer> searchByNameOrEmail(@Param("term") String term);

    @Query("SELECT c FROM Customer c WHERE " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "AND c.status = :status")
    List<Customer> searchByNameOrEmailAndStatus(
            @Param("term") String term,
            @Param("status") Customer.CustomerStatus status);

    Page<Customer> findByStatus(Customer.CustomerStatus status, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :term, '%'))")
    Page<Customer> searchByNameOrEmail(@Param("term") String term, Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "AND c.status = :status")
    Page<Customer> searchByNameOrEmailAndStatus(
            @Param("term") String term,
            @Param("status") Customer.CustomerStatus status,
            Pageable pageable);

    long count();

    boolean existsByEmail(String email);
}
