package com.aisa.provider.web;

import com.aisa.provider.contract.UniformMessage;
import com.aisa.provider.contract.UniformRequest;
import com.aisa.provider.contract.UniformResponse;
import com.aisa.provider.gateway.AiProviderGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the AI Provider Gateway's completion capability
 * for inter-service HTTP calls.
 *
 * <p>Agent workers (a separate microservice) call this endpoint to get AI completions
 * without importing the gateway as a Maven dependency. This keeps the microservice
 * boundary clean (Requirement 20.4).
 *
 * <p>Endpoint: {@code POST /api/v1/provider/complete}
 */
@RestController
@RequestMapping("/api/v1/provider")
public class ProviderCompletionController {

    private static final Logger log = LoggerFactory.getLogger(ProviderCompletionController.class);

    private final AiProviderGateway gateway;

    public ProviderCompletionController(AiProviderGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Execute a blocking AI completion. Accepts a prompt string and returns the
     * generated content.
     *
     * <p>The prompt is wrapped into a {@link UniformRequest} with a single user message
     * and default options. The gateway handles provider selection, timeout, failover,
     * and usage recording.
     *
     * @param request the completion request containing the prompt
     * @return the generated content, provider info, and finish reason
     */
    @PostMapping("/complete")
    public ResponseEntity<CompletionResponse> complete(@RequestBody CompletionRequest request) {
        log.info("Received completion request ({} chars)", request.prompt().length());

        UniformRequest uniformRequest = UniformRequest.ofPrompt(request.prompt());
        UniformResponse response = gateway.complete(uniformRequest);

        log.info("Completion served by provider: {}", response.servedBy());

        return ResponseEntity.ok(new CompletionResponse(
                response.content(),
                response.servedBy().name(),
                response.finishReason()
        ));
    }

    /**
     * Request body for the completion endpoint.
     *
     * @param prompt the full prompt to send to the AI provider
     */
    public record CompletionRequest(String prompt) {
    }

    /**
     * Response body from the completion endpoint.
     *
     * @param content      the AI-generated text
     * @param provider     the name of the provider that served the request
     * @param finishReason the completion finish reason
     */
    public record CompletionResponse(String content, String provider, String finishReason) {
    }
}
