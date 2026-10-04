package com.billing.backend.repository;

import com.billing.backend.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * SystemSettingsRepository — queries for the "system_settings" table.
 *
 * This table always has exactly ONE row (id = 1).
 * We use findById(1) to get it and save(settings) to update it.
 *
 * No custom methods needed — JpaRepository provides everything.
 */
@Repository
public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Integer> {
    // JpaRepository already gives us:
    //   findById(1)        → get the single settings row
    //   save(settings)     → update the settings row
    //   existsById(1)      → check if settings row exists
}
