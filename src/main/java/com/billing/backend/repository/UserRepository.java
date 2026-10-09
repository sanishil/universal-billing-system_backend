package com.billing.backend.repository;

import com.billing.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    Optional<User> findByEmailOrUsername(String email, String username);

    Optional<User> findByEmailIgnoreCase(String email);
}
