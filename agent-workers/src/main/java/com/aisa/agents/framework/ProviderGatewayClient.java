package com.aisa.agents.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * HTTP client for calling the AI Provider Gateway's completion endpoint.
 *
 * <p>Agent workers are a separate microservice from the AI Provider Gateway, so they
 * communicate over HTTP rather than importing the gateway as a Maven module. This keeps
 * the microservice boundary clean and allows independent scaling.
 *
 * <p>The client calls {@code POST /api/v1/provider/complete} with a JSON body containing
 * the prompt, and returns the AI-generated text response.
 *
 * <p>Timeout is set to 120 seconds to match the agent invocation timeout (Req 6.3).
 */
@Component
public class ProviderGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderGatewayClient.class);

    private final RestClient restClient;

    public ProviderGatewayClient(
            @Value("${aisa.provider-gateway.base-url:http://localhost:8084}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        log.info("ProviderGatewayClient initialized with base URL: {}", baseUrl);
    }

    /**
     * Constructor for testing — accepts a pre-configured RestClient.
     */
    ProviderGatewayClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Send a prompt to the AI Provider Gateway and return the generated text.
     *
     * @param prompt the full prompt to send to the AI provider
     * @return the AI-generated response text
     * @throws ProviderGatewayException if the call fails or times out
     */
    public String complete(String prompt) {
        try {
            log.debug("Sending completion request to Provider Gateway ({} chars)", prompt.length());

            CompletionResponse response = restClient.post()
                    .uri("/api/v1/provider/complete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CompletionRequest(prompt))
                    .retrieve()
                    .body(CompletionResponse.class);

            if (response == null || response.content() == null) {
                throw new ProviderGatewayException("Provider Gateway returned null response");
            }

            log.debug("Received completion response ({} chars)", response.content().length());
            return response.content();

        } catch (RestClientException e) {
            log.error("Provider Gateway call failed: {}", e.getMessage());
            throw new ProviderGatewayException(
                    "Provider Gateway call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Request body for the completion endpoint.
     */
    public record CompletionRequest(String prompt) {
    }

    /**
     * Response body from the completion endpoint.
     */
    public record CompletionResponse(String content, String provider, String finishReason) {
    }

    /**
     * Exception thrown when the Provider Gateway call fails.
     */
    public static class ProviderGatewayException extends RuntimeException {
        public ProviderGatewayException(String message) {
            super(message);
        }

        public ProviderGatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
