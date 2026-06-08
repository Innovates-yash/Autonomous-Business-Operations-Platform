# AI Software Architect Platform

A production-grade, AI-powered platform that turns a plain-language business idea into a complete, reviewable software architecture blueprint. A coordinated set of ten specialized AI agents performs requirement analysis, business analysis, product planning, system architecture, database design, security design, API design, DevOps design, cost estimation, and documentation, then assembles the results into a single versioned Blueprint that a human must review and approve before any implementation proceeds.

The full specification (requirements, design, implementation plan) lives in
`.kiro/specs/ai-software-architect-platform/`.

## Architecture

Microservices behind a Spring Cloud Gateway, with Kafka for event-driven agent
orchestration, Redis for shared state and caching, and MySQL for durable persistence.

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | Routing, TLS, rate limiting, JWT validation, correlation IDs |
| auth-service | 8081 | Authentication, tokens, OAuth2, authorization |
| project-service | 8082 | Project lifecycle and requirement analysis |
| orchestrator-service | 8083 | Multi-agent saga orchestration |
| ai-provider-gateway | 8084 | Uniform AI provider abstraction with failover |
| ai-chat-service | 8085 | Conversational interface with streaming |
| blueprint-service | 8086 | Blueprint assembly, versioning, approval |
| export-service | 8087 | PDF / Markdown export |
| notification-service | 8088 | WebSocket fan-out of progress |
| audit-service | 8089 | Immutable audit trail |
| agent-workers | 8090 | The ten specialized AI agents |

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (Docker Desktop) for local infrastructure
- Node.js 20+ (for the frontend, added in a later task)

## Build

```bash
mvn clean verify
```

## Run local infrastructure

```bash
docker compose -f docker-compose.infra.yml up -d
```

This starts MySQL, Redis, Kafka, Prometheus, Grafana, the ELK stack, and Zipkin.

| Tool | URL |
|---|---|
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 (admin/admin) |
| Kibana | http://localhost:5601 |
| Zipkin | http://localhost:9411 |

## Run a service

```bash
mvn -pl auth-service spring-boot:run
```

## Build a service image

```bash
docker build --build-arg MODULE=auth-service -t aisa/auth-service .
```

## Configuration and secrets

AI provider API keys (OpenAI, Gemini, Claude) and database credentials are supplied
via environment variables or a local `application-local.yml` (git-ignored). Never
commit secrets.
