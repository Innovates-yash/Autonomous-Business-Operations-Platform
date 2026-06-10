package com.aisa.auth.repository;

import com.aisa.auth.domain.Role;
import com.aisa.auth.domain.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Role} entities. The six platform roles
 * are seeded by Flyway migration {@code V1__init_auth_schema.sql}; registration
 * looks up {@link RoleName#GUEST} to assign the default role (Requirement 2.2).
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
