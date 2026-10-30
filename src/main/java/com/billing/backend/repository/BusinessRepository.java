package com.billing.backend.repository;

import com.billing.backend.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, String> {

    List<Business> findByUserIdOrderByCreatedAtAsc(String userId);

    Optional<Business> findByIdAndUserId(String id, String userId);
}
