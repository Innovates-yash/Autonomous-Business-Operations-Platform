package com.aisa.auth.repository;

import com.aisa.auth.domain.OAuthIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link OAuthIdentity} entities. Supports the
 * OAuth2 authorization-code flow by looking up an existing identity link for a
 * given provider and provider-subject pair (Requirements 1.8, 1.13).
 */
@Repository
public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {

    /**
     * Finds an existing OAuth identity link by provider key and provider subject.
     *
     * @param provider       the provider key (e.g. "google", "github")
     * @param providerUserId the subject identifier returned by the provider
     * @return the linked identity if it exists
     */
    Optional<OAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
}
