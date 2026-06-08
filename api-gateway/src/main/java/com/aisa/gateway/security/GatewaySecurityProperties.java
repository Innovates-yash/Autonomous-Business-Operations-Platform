package com.aisa.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Edge transport-security configuration for the API Gateway.
 *
 * <p>Supports Requirements 25.1 and 25.2: all client-server traffic must travel over
 * encrypted transport, and connections arriving over an unencrypted channel must be
 * rejected without processing the request payload.
 *
 * <p>Enforcement is configurable per environment so that local development can run over
 * plaintext HTTP while staging and production enforce TLS. When TLS is terminated by an
 * upstream load balancer or ingress rather than the gateway itself, the gateway trusts
 * the forwarded protocol headers it sets (for example {@code X-Forwarded-Proto}).
 */
@ConfigurationProperties(prefix = "aisa.gateway.security")
public class GatewaySecurityProperties {

    /**
     * When {@code true}, the gateway rejects any request that did not arrive over an
     * encrypted transport channel. Defaults to {@code false} for local development.
     */
    private boolean requireSecureTransport = false;

    public boolean isRequireSecureTransport() {
        return requireSecureTransport;
    }

    public void setRequireSecureTransport(boolean requireSecureTransport) {
        this.requireSecureTransport = requireSecureTransport;
    }
}
