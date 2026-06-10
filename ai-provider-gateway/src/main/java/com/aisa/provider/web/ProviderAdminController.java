package com.aisa.provider.web;

import com.aisa.provider.model.ProviderType;
import com.aisa.provider.repository.ProviderConfigRepository;
import com.aisa.provider.selection.ProviderSelectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin API for AI provider selection and inspection (Requirement 20.1, 20.2, 20.3).
 *
 * <p>Security note: these are administrative operations. Authentication and the Admin-role
 * authorization check are enforced upstream at the API Gateway / Auth Service (Requirement 2);
 * the gateway propagates the authenticated principal in {@code X-User-Id}, which is recorded as
 * the {@code selectedBy} of each selection. This service does not re-authenticate.
 */
@RestController
@RequestMapping("/api/admin/providers")
public class ProviderAdminController {

    private final ProviderSelectionService selectionService;
    private final ProviderConfigRepository configRepository;

    public ProviderAdminController(ProviderSelectionService selectionService,
                                   ProviderConfigRepository configRepository) {
        this.selectionService = selectionService;
        this.configRepository = configRepository;
    }

    /** List the configured providers (Req 20.1). */
    @GetMapping
    public List<ProviderConfigView> listProviders() {
        return configRepository.findAll().stream()
                .map(ProviderConfigView::from)
                .toList();
    }

    /** Return the currently active provider (Req 20.2). */
    @GetMapping("/selection")
    public ActiveProviderResponse currentSelection() {
        ProviderType active = selectionService.currentSelection().orElse(null);
        boolean routable = selectionService.activeClient().isPresent();
        return new ActiveProviderResponse(active, routable);
    }

    /**
     * Select the active provider (Req 20.2). Rejects an unconfigured provider and retains the
     * prior selection (Req 20.3) — handled by {@link ProviderAdminExceptionHandler}.
     */
    @PostMapping("/selection")
    public ResponseEntity<ActiveProviderResponse> selectProvider(
            @Valid @RequestBody SelectProviderRequest request,
            @RequestHeader(name = "X-User-Id", required = false, defaultValue = "system") String adminId) {
        ProviderType active = selectionService.selectProvider(request.provider(), adminId);
        boolean routable = selectionService.activeClient().isPresent();
        return ResponseEntity.ok(new ActiveProviderResponse(active, routable));
    }
}
