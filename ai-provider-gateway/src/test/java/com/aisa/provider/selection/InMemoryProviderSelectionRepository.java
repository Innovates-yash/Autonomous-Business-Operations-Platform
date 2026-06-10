package com.aisa.provider.selection;

import com.aisa.provider.model.ProviderSelection;
import com.aisa.provider.repository.ProviderSelectionRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A real, hand-written in-memory {@link ProviderSelectionRepository} for unit tests — no mocking
 * framework. Preserves insertion order and answers the most-recent and history queries used by
 * the selection service.
 */
class InMemoryProviderSelectionRepository implements ProviderSelectionRepository {

    private final List<ProviderSelection> selections = new ArrayList<>();

    @Override
    public ProviderSelection save(ProviderSelection selection) {
        selections.add(selection);
        return selection;
    }

    @Override
    public Optional<ProviderSelection> findTopByOrderBySelectedAtDesc() {
        // Iterate so that, on equal timestamps, the most recently saved selection wins —
        // mirroring a real store ordered by selected_at then insertion.
        ProviderSelection best = null;
        for (ProviderSelection s : selections) {
            if (best == null || !s.getSelectedAt().isBefore(best.getSelectedAt())) {
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public List<ProviderSelection> findAllByOrderBySelectedAtDesc() {
        return selections.stream()
                .sorted(Comparator.comparing(ProviderSelection::getSelectedAt).reversed())
                .toList();
    }

    int size() {
        return selections.size();
    }
}
