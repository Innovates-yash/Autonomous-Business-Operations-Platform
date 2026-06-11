# Inter-Service Mutual TLS (mTLS)

## Overview

All internal service-to-service communication in staging and production environments is
protected by mutual TLS (mTLS). Each microservice presents its own X.509 certificate and
validates the peer certificate against a shared trust store, ensuring both parties are
authenticated at the transport layer before any application-level data is exchanged.

This satisfies **Requirement 25.1** (at-rest and in-transit encryption for sensitive data)
and complements the edge TLS termination performed by the API Gateway.

## Architecture

```
┌──────────────┐        mTLS        ┌──────────────────┐
│  API Gateway │◄──────────────────►│   Auth Service   │
│  (TLS edge)  │                    │  (server cert)   │
└──────┬───────┘                    └──────────────────┘
       │ mTLS
       ▼
┌──────────────────┐   mTLS   ┌─────────────────────┐
│ Project Service  │◄─────────►│ Orchestrator Service│
│  (server cert)   │           │   (server cert)     │
└──────────────────┘           └──────────┬──────────┘
                                          │ mTLS
                                          ▼
                               ┌─────────────────────┐
                               │  Agent Workers /    │
                               │  AI Provider GW /   │
                               │  Other services     │
                               └─────────────────────┘
```

- **Edge (API Gateway):** Terminates external TLS from clients. Communicates with
  downstream services over mTLS when `SERVICE_MTLS_ENABLED=true`.
- **Internal services:** Each service has `server.ssl.client-auth=need`, requiring the
  caller to present a valid client certificate from the shared trust store.

## Certificate Layout

| Artifact | Purpose | Format | Default Path |
|----------|---------|--------|--------------|
| Service keystore | Contains the service's private key and signed certificate | PKCS12 | `classpath:tls/<service-name>.p12` |
| Shared trust store | Contains the CA certificate(s) trusted by all services | PKCS12 | `classpath:tls/truststore.p12` |

All keystores are PKCS12 format for cross-platform compatibility.

## Configuration

Each service's `application.yml` contains an mTLS block in the `staging` and `prod`
profiles:

```yaml
server:
  ssl:
    enabled: ${SERVICE_MTLS_ENABLED:true}
    key-store: ${SERVICE_SSL_KEYSTORE:classpath:tls/<service-name>.p12}
    key-store-password: ${SERVICE_SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    client-auth: need
    trust-store: ${SERVICE_SSL_TRUSTSTORE:classpath:tls/truststore.p12}
    trust-store-password: ${SERVICE_SSL_TRUSTSTORE_PASSWORD}
    trust-store-type: PKCS12
```

### Environment Variables

| Variable | Description |
|----------|-------------|
| `SERVICE_MTLS_ENABLED` | Enable/disable mTLS (default: `true` in staging/prod) |
| `SERVICE_SSL_KEYSTORE` | Path to the service's PKCS12 keystore |
| `SERVICE_SSL_KEYSTORE_PASSWORD` | Password for the service keystore |
| `SERVICE_SSL_TRUSTSTORE` | Path to the shared trust store |
| `SERVICE_SSL_TRUSTSTORE_PASSWORD` | Password for the trust store |

## Certificate Generation (Development / CI)

For local development and CI pipelines, generate a self-signed CA and per-service
certificates using the following approach:

```bash
# 1. Create a self-signed CA
keytool -genkeypair -alias ca -keyalg RSA -keysize 4096 \
  -validity 3650 -storetype PKCS12 \
  -keystore ca.p12 -storepass changeit \
  -dname "CN=AISA Internal CA, O=AISA, L=Dev"

# 2. Export CA certificate
keytool -exportcert -alias ca -keystore ca.p12 -storepass changeit \
  -file ca.crt -rfc

# 3. Create a service keystore and sign with the CA
keytool -genkeypair -alias auth-service -keyalg RSA -keysize 2048 \
  -validity 365 -storetype PKCS12 \
  -keystore auth-service.p12 -storepass changeit \
  -dname "CN=auth-service, O=AISA"

keytool -certreq -alias auth-service -keystore auth-service.p12 \
  -storepass changeit -file auth-service.csr

keytool -gencert -alias ca -keystore ca.p12 -storepass changeit \
  -infile auth-service.csr -outfile auth-service-signed.crt -validity 365

keytool -importcert -alias ca -keystore auth-service.p12 \
  -storepass changeit -file ca.crt -noprompt

keytool -importcert -alias auth-service -keystore auth-service.p12 \
  -storepass changeit -file auth-service-signed.crt

# 4. Create shared trust store with the CA cert
keytool -importcert -alias ca -storetype PKCS12 \
  -keystore truststore.p12 -storepass changeit \
  -file ca.crt -noprompt
```

Repeat step 3 for each service (project-service, orchestrator-service, etc.).

## Production Deployment

In production (Kubernetes):

1. **Certificate issuance** is handled by cert-manager with an internal ClusterIssuer.
2. **Keystores** are mounted as Kubernetes Secrets into each pod at a well-known path.
3. **Trust store** is a shared Secret containing the internal CA bundle, mounted into
   every service pod.
4. **Rotation** is automated via cert-manager renewal; pods pick up new certificates
   on restart (rolling update).

## Disabling mTLS

For local development (`dev` profile), mTLS is not enabled — the `server.ssl` block is
only present in staging and prod profiles. Services communicate over plain HTTP on
localhost during development, backed by docker-compose networking.

To explicitly disable mTLS in a non-dev environment (e.g., when an external service mesh
handles mTLS), set:

```bash
SERVICE_MTLS_ENABLED=false
```

## Relationship to Edge TLS

| Layer | Mechanism | Terminates At |
|-------|-----------|---------------|
| Client → Gateway | External TLS (Let's Encrypt / ACM cert) | API Gateway |
| Gateway → Services | mTLS (internal CA) | Each downstream service |
| Service → Service | mTLS (internal CA) | Each peer service |

The API Gateway terminates external TLS and then uses mTLS for all downstream calls,
maintaining end-to-end encryption.
