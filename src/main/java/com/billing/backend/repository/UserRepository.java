package com.billing.backend.repository;

import com.billing.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository — handles all database queries for the "users" table.
 *
 * HOW REPOSITORIES WORK IN SPRING:
 * ─────────────────────────────────
 * You just declare an interface extending JpaRepository<EntityClass, PrimaryKeyType>.
 * Spring automatically generates the implementation with full SQL!
 *
 * JpaRepository already gives you for FREE:
 *   save(user)           → INSERT or UPDATE
 *   findById("USR-01")   → SELECT * FROM users WHERE id = 'USR-01'
 *   findAll()            → SELECT * FROM users
 *   delete(user)         → DELETE FROM users WHERE id = ?
 *   count()              → SELECT COUNT(*) FROM users
 *   existsById("USR-01") → SELECT EXISTS (...)
 *
 * For CUSTOM queries, Spring reads your method name and auto-generates SQL.
 * For example:
 *   findByEmail(email) → SELECT * FROM users WHERE email = ?
 *   findByUsername(username) → SELECT * FROM users WHERE username = ?
 *
 * Optional<User> means: the query might return nothing (instead of null crash).
 * Usage: userRepo.findByEmail("admin@ubs.com").orElseThrow(() → new Exception("Not found"))
 *
 * @Repository marks this as a Spring-managed bean (component)
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // Used during login when user submits an email address
    // SQL: SELECT * FROM users WHERE email = ?
    Optional<User> findByEmail(String email);

    // Used during login when user submits a username (no @ symbol)
    // SQL: SELECT * FROM users WHERE username = ?
    Optional<User> findByUsername(String username);

    // Used during registration to check if email is already taken
    // SQL: SELECT EXISTS (SELECT 1 FROM users WHERE email = ?)
    boolean existsByEmail(String email);

    // Used to find user by EITHER email OR username
    // Spring reads "Or" in the method name and generates: WHERE email = ? OR username = ?
    Optional<User> findByEmailOrUsername(String email, String username);

    // Used for account status check endpoint
    // SQL: SELECT * FROM users WHERE email = ?
    Optional<User> findByEmailIgnoreCase(String email);
}
