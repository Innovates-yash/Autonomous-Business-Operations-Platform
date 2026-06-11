package com.aisa.auth.repository;

import com.aisa.auth.domain.Permission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Permission} entities.
 *
 * <p>Permissions are seeded by the Flyway migration {@code V2__seed_rbac_matrix.sql}
 * and looked up at authorization-decision time via the role's permission set.
 * The {@link #findByName(String)} query supports programmatic permission checks
 * (Requirement 2.3).
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByName(String name);
}
