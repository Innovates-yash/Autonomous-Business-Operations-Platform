package com.aisa.provider.repository;

import com.aisa.provider.model.ProviderSelection;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link ProviderSelection} (Requirement 20.2).
 *
 * <p>Selections form an append-only history. The most recent row by {@code selectedAt} is the
 * active selection; the gateway loads it on startup and on each refresh tick so a saved
 * selection takes effect within 5 seconds (Req 20.2). A rejected (unconfigured) selection is
 * never saved, which is what preserves the previously selected provider (Req 20.3).
 */
public interface ProviderSelectionRepository extends Repository<ProviderSelection, String> {

    ProviderSelection save(ProviderSelection selection);

    Optional<ProviderSelection> findTopByOrderBySelectedAtDesc();

    List<ProviderSelection> findAllByOrderBySelectedAtDesc();
}
