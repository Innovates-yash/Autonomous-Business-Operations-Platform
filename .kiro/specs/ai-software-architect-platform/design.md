# Design Document

## Overview

The AI Software Architect Platform is a cloud-native, microservices-based system that turns a plain-language business idea into a complete, reviewable software architecture blueprint. A coordinated set of ten specialized AI agents performs requirement analysis, business analysis, product planning, system architecture, database design, security design, API design, DevOps design, cost estimation, and documentation, then assembles the results into a single versioned Blueprint that a human must review and approve before any implementation proceeds.

This document defines the technical design that satisfies the 30 requirements captured in `requirements.md`. It describes the architecture, service boundaries, the multi-agent orchestration model, the AI provider abstraction, the conceptual data model, and the cross-cutting concerns of security, scalability, observability, and production readiness. Consistent with Phase 1 scope, this document deliberately stops at the design level: it identifies entities, service boundaries, and communication patterns, but does not contain source code, runnable folder structures, concrete endpoint signatures, or physical database schemas. Those are deliverables of later, approved implementation phases.

### Design Goals

- **Approval-gated by construction.** No implementation-phase action can run for a Project that is not in the `Approved` state. The approval gate is a first-class architectural element, not a UI convention.
- **Provider-agnostic AI.** Every agent talks to a single uniform gateway contract. Swapping OpenAI for Gemini, Claude, or a Local LLM is a configuration change, never a code change in an agent.
- **Resilient orchestration.** Agent failures retry, isolate, and preserve completed work rather than discarding an entire generation run.
- **Horizontally scalable and stateless services.** Shared session and cache state lives in Redis so any instance can serve any request; long-running work is offloaded to Kafka.
- **Observable and recoverable.** Every service emits metrics, structured logs, and traces with a propagated correlation identifier, and persistent data is backed up and restorable.

### Design Decisions and Rationale

| Decision | Rationale | Alternatives Considered |
|---|---|---|
| Microservices over modular monolith | Independent scaling of AI-heavy orchestration vs. lightweight CRUD; clear service boundaries map to Spring Cloud deployment units; aligns with Requirement 26 horizontal scaling | Modular monolith — simpler ops but couples AI workload scaling to the whole app |
| Kafka for agent orchestration | Long-running (up to 120s/agent) work must be async and survive instance loss (Req 6, 26); event log enables progress streaming and re-queue on failure | Synchronous REST chaining — blocks threads, no durability, no replay |
| Redis for shared state + cache | Stateless services require externalized session/cache (Req 26); also serves chat-context and rate-limit counters (Req 25) | Sticky sessions — breaks horizontal scaling and failover |
| Provider Gateway abstraction via Spring AI | Uniform contract across OpenAI/Gemini/Claude/Local LLM with failover (Req 20) | Per-provider SDK calls in each agent — vendor lock-in, duplicated failover logic |
| Saga-style orchestration with a dedicated orchestrator service | Dependency-ordered agent execution with retry/halt semantics (Req 6); central place to record per-agent timing and outcomes | Choreography-only — harder to enforce dependency order and global halt |
| WebSocket (STOMP) for real-time | Streaming chat tokens and per-agent progress within 2s (Req 5, 22, 29) | Polling — higher latency and load |

## Architecture

### High-Level System Architecture

```
                                   ┌─────────────────────────────────────────┐
                                   │              Web_Client (SPA)            │
                                   │  React + TS + Vite + Redux + RQ + MUI    │
                                   │   REST (React Query)   WS (STOMP)        │
                                   └───────────────┬───────────────┬─────────┘
                                                   │ HTTPS         │ WSS
                                   ┌───────────────▼───────────────▼─────────┐
                                   │            API Gateway                   │
                                   │  Spring Cloud Gateway                    │
                                   │  TLS term · routing · rate limit · authN │
                                   └───┬───────┬───────┬───────┬───────┬──────┘
                                       │       │       │       │       │
              ┌────────────────────────┘       │       │       │       └────────────────────────┐
              │                ┌───────────────┘       │       └───────────────┐                │
        ┌─────▼──────┐   ┌─────▼──────┐         ┌───────▼───────┐        ┌───────▼──────┐  ┌──────▼───────┐
        │   Auth      │   │  Project   │         │  Orchestrator │        │   AI Chat    │  │ Notification │
        │  Service    │   │  Service   │         │   Service     │        │   Service    │  │   Service    │
        │(authN/authZ)│   │(lifecycle) │         │(agent saga)   │        │(WS streaming)│  │ (WS fan-out) │
        └─────┬──────┘   └─────┬──────┘         └───┬───────┬───┘        └──────┬───────┘  └──────┬───────┘
              │                │                    │       │                   │                 │
              │                │                    │       ▼                   │                 │
              │                │                    │  ┌──────────────┐         │                 │
              │                │                    │  │ AI Provider  │◄────────┘                 │
              │                │                    │  │   Gateway    │                           │
              │                │                    │  │(OpenAI/Gemini│                           │
              │                │                    │  │ Claude/Local)│                           │
              │                │                    │  └──────────────┘                           │
        ┌─────▼──────┐   ┌─────▼──────┐      ┌───────▼───────┐  ┌───────────────┐  ┌──────────────▼─┐
        │  Blueprint  │   │  Export    │      │ Agent Workers │  │  Audit        │  │  (other infra) │
        │  Service    │   │  Service   │      │ (10 agents)   │  │  Service      │  │                │
        │(assemble/   │   │(PDF/MD)    │      │               │  │               │  │                │
        │ approve)    │   │            │      │               │  │               │  │                │
        └─────┬──────┘   └─────┬──────┘      └───────┬───────┘  └──────┬────────┘  └────────────────┘
              │                │                     │                 │
   ┌──────────┴────────────────┴─────────────────────┴─────────────────┴───────────────────────────┐
   │                                     Backing Services                                            │
   │   ┌──────────┐   ┌──────────┐   ┌──────────────┐   ┌──────────────────────────────────────┐    │
   │   │  MySQL    │   │  Redis    │   │ Apache Kafka │   │ Observability: Prometheus/Grafana,   │    │
   │   │(persist)  │   │(cache/    │   │ (events/     │   │ ELK (logs), Zipkin (traces)          │    │
   │   │           │   │ session)  │   │ work queue)  │   │                                      │    │
   │   └──────────┘   └──────────┘   └──────────────┘   └──────────────────────────────────────┘    │
   └─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Architectural Style

The Platform follows a **microservices architecture** behind a single **API Gateway**, with **event-driven** inter-service communication for long-running work and **synchronous REST** for interactive CRUD. Services are stateless; all durable state is in MySQL, all ephemeral shared state (sessions, caches, rate-limit counters, chat context windows) is in Redis, and all asynchronous workflow coordination flows through Kafka. This directly serves Requirement 26 (horizontal scaling, shared state, async processing) and Requirement 6 (durable, ordered agent orchestration).

### Service Inventory

| Service | Responsibility | Key Requirements | Sync/Async |
|---|---|---|---|
| API Gateway | TLS termination, routing, edge rate limiting, JWT validation, correlation-ID injection | 2, 25, 27 | Sync |
| Auth Service | Registration, login, JWT/refresh issuance, OAuth2, lockout, role assignment, authZ decisions | 1, 2 | Sync |
| Project Service | Project CRUD, state machine, ownership, requirement analysis coordination | 3, 4 | Sync + Async |
| Orchestrator Service | Agent saga: dependency-ordered invocation, retry, halt, progress events | 6, 17, 18 | Async |
| Agent Workers (×10) | The ten specialized AI agents producing Design_Artifacts | 7–16 | Async |
| AI Provider Gateway | Uniform provider contract, selection, failover, usage recording | 20 | Sync (internal) |
| AI Chat Service | Conversational interface, streaming, context windows | 5, 29 | Sync + WS |
| Blueprint Service | Blueprint assembly, versioning, approval workflow | 18, 19 | Sync + Async |
| Export Service | PDF/Markdown rendering, watermarking, versioned export | 21 | Sync |
| Notification Service | WebSocket fan-out of progress and state-change events | 22, 29 | Async + WS |
| Audit Service | Immutable audit event capture, query, retention | 23 | Async + Sync |

### Technology Stack Mapping (Constraint per Requirement 30)

| Concern | Technology | Where Used |
|---|---|---|
| Frontend | React, TypeScript, Vite, Tailwind, Redux Toolkit, React Query, Material UI, WebSocket (STOMP) | Web_Client |
| Backend services | Java 21, Spring Boot 3, Spring Web, Spring Validation, Spring Data JPA | All services |
| Service mesh / edge | Spring Cloud (Gateway, Config, discovery) | Gateway, all services |
| AI integration | Spring AI | AI Provider Gateway, Agent Workers |
| Security | Spring Security, JWT, refresh tokens, OAuth2 | Auth Service, Gateway |
| Persistence | MySQL | All stateful services |
| Cache / shared state | Redis | Sessions, chat context, rate limits, hot reads |
| Messaging | Apache Kafka | Orchestration events, work queues, audit stream |
| Containerization | Docker, Kubernetes | All services |
| CI/CD | GitHub Actions | Build/test/deploy pipeline |
| Monitoring | Prometheus, Grafana | All services |
| Logging | ELK Stack | All services |
| Tracing | Zipkin | All services |
| Cloud | AWS-ready (EKS, RDS, ElastiCache, MSK, S3) | Deployment target |

## Components and Interfaces

This section describes each service's responsibility, its primary interface contracts (described conceptually, not as concrete endpoint signatures), its data ownership, and the requirements it satisfies.

### API Gateway

- **Responsibility:** Single entry point for all client traffic. Terminates TLS (Req 25.1), rejects unencrypted connections (Req 25.2), routes to downstream services, enforces edge rate limiting (Req 25.4–25.5), validates JWT access tokens, and injects or generates the correlation identifier (Req 27.5–27.6).
- **Interfaces (conceptual):** REST passthrough for all `/api/*` domains; WebSocket upgrade passthrough for `/ws/*`. Adds `X-Correlation-Id` and authenticated principal claims to downstream requests.
- **Data ownership:** None (stateless). Rate-limit counters live in Redis keyed by principal + window.
- **Notes:** Rate limiting at the edge is the first line; per-service validation remains authoritative for business rules.

### Auth Service

- **Responsibility:** Identity and access. Owns registration, credential verification, JWT access token (15-min TTL) and refresh token (7-day TTL) issuance, refresh rotation, OAuth2 authorization-code exchange, account lockout after 5 failed attempts in 15 minutes, sign-out invalidation, role assignment, and authorization decisions.
- **Interfaces (conceptual):**
  - Authentication domain: register, login, refresh, logout, OAuth2 callback.
  - Authorization domain: `decide(principal, action, resource) -> Permit | Deny`, returned within 500 ms (Req 2.4).
  - Admin domain: assign/change role, with cache invalidation of prior-role permissions (Req 2.13–2.14).
- **Data ownership:** `User`, `Role`, `Permission`, `RefreshToken`, `LoginAttempt`, `OAuthIdentity`.
- **Token strategy:** Access tokens are short-lived JWTs carrying `sub`, `role`, `iat`, `exp`. Refresh tokens are opaque, stored hashed, single-use (rotated on each refresh), and revocable. Logout and password/role change invalidate active refresh tokens.
- **Requirements:** 1, 2.

### Project Service

- **Responsibility:** Owns the Project aggregate and its state machine; coordinates requirement analysis via the Requirement_Analysis_Module; enforces ownership and view scoping (Req 3.6).
- **State machine (Req 3.4, 3.9, 3.10, 19):**

```
        create
   ─────────────▶ ┌────────┐  startAnalysis  ┌───────────┐  confirmAnalysis ┌────────────┐
                  │ Draft  │ ───────────────▶│ Analyzing │ ────────────────▶│ Generating │
                  └────┬───┘                 └─────┬─────┘                  └─────┬──────┘
                       │                            │                             │ assembled
                       │                            │                             ▼
                       │                            │                       ┌────────────┐
                       │                            │       requestChanges  │ In_Review  │
                       │                            │     ┌─────────────────┤            │
                       │                            ▼     ▼                 └─────┬──────┘
                       │                      ┌──────────────────┐                │ approve
                       │                      │ Changes_Requested│                ▼
                       │                      └────────┬─────────┘          ┌────────────┐
                       │                               │ regenerate         │  Approved  │
                       │                               └───────────────────▶│            │
                       │                                  (→ Generating)    └────────────┘
                       │
                       └────────── archive (from any state) ──────────▶  ┌────────────┐
                                                                          │  Archived  │
                                                                          └────────────┘
```

  Invalid transitions are rejected with state preserved (Req 3.10). Every transition is recorded with timestamp and initiating User (Req 3.8).
- **Data ownership:** `Project`, `Idea`, `Requirement`, `UseCase`, `ClarifyingQuestion`, `ProjectStateTransition`.
- **Requirements:** 3, 4.

### Requirement Analysis Module

- **Responsibility:** Produces structured functional/non-functional requirements and use cases from an Idea, generates 1–10 clarifying questions, incorporates answers, and supports manual edit while in `Analyzing` (Req 4). Runs as a capability within Project Service that calls the AI Provider Gateway. Retries provider failures up to 3 times and preserves prior state on exhaustion (Req 4.9).
- **Interfaces (conceptual):** analyze(idea) → {requirements, useCases, questions}; answerQuestions(answers) → updated requirements; editRequirement(...).

### Orchestrator Service

- **Responsibility:** The heart of Blueprint generation. Implements a saga that invokes the ten agents in dependency order, supplies prerequisite outputs as input, enforces a 120s per-agent timeout, retries up to 4 total attempts, halts transitive dependents on failure while preserving completed outputs, records per-invocation timing/outcome, publishes progress events, and signals Blueprint assembly on success (Req 6).
- **Agent dependency DAG (Req 6.1, 6.10):**

```
                       ┌────────────────────────┐
                       │ Requirement_Analyst     │  (R7)
                       └───────────┬─────────────┘
                                   │
                       ┌───────────▼─────────────┐
                       │ Business_Analyst         │  (R8)
                       └───────────┬─────────────┘
                                   │
                       ┌───────────▼─────────────┐
                       │ Product_Manager          │  (R9)
                       └───────────┬─────────────┘
                                   │
                       ┌───────────▼─────────────┐
                       │ Software_Architect       │  (R10)
                       └──┬──────────┬─────────┬──┘
            ┌─────────────┘          │         └──────────────┐
   ┌────────▼────────┐    ┌──────────▼───────┐     ┌───────────▼────────┐
   │ Database_Arch    │   │  API_Architect    │     │ Security_Architect │   (R11, R13, R12)
   │ (R11)            │   │  (R13)            │     │ (R12)              │
   └────────┬─────────┘   └──────────┬───────┘     └───────────┬────────┘
            └────────────┬───────────┴──────────────┬──────────┘
                ┌────────▼─────────┐        ┌────────▼─────────┐
                │ DevOps_Architect  │       │ Cost_Estimation   │   (R14, R15)
                │ (R14)             │       │ (R15)             │
                └────────┬─────────┘        └────────┬─────────┘
                         └────────────┬──────────────┘
                            ┌─────────▼──────────┐
                            │ Documentation       │  (R16)
                            └─────────┬──────────┘
                                      ▼
                            ┌────────────────────┐
                            │ Blueprint Assembly  │  (R18)
                            └────────────────────┘
```

  Database_Architect, API_Architect, and Security_Architect have no mutual dependency and may run concurrently (Req 6.10); DevOps and Cost Estimation likewise run in parallel once their prerequisites complete.
- **Execution model:** Each agent step is a Kafka-driven task. The orchestrator maintains a `GenerationRun` with per-step `AgentInvocation` records (start, end, outcome ∈ {success, failed, timed_out}, attempt count). Step results are persisted so a re-queued worker (Req 26.6) does not repeat completed work.
- **Data ownership:** `GenerationRun`, `AgentInvocation`, `AgentOutput`.
- **Requirements:** 6, 17 (roadmap is produced as part of the run), 18 (triggers assembly).

### Agent Workers (Ten Specialized Agents)

Each agent is a stateless worker that consumes a task from Kafka, reads prerequisite `AgentOutput`s, calls the AI Provider Gateway with an agent-specific prompt template, validates that its output is non-empty and conforms to the expected Design_Artifact structure, and writes its `AgentOutput`. Agent-specific responsibilities and contracts are detailed in the **AI Agent Architecture** section. Requirements 7–16.

### AI Provider Gateway

- **Responsibility:** Presents one uniform request/response contract to all agents and the chat service regardless of provider; routes to the Admin-selected provider; classifies a provider unavailable on timeout (default 30s, configurable 1–120s) or 3 consecutive errors; fails over through up to 3 configured fallbacks in priority order; returns a provider-unavailable error when no fallback succeeds; records provider + timestamp per request with ≥90-day retention (Req 20).
- **Interfaces (conceptual):** `complete(uniformRequest) -> uniformResponse`; `stream(uniformRequest) -> tokenStream`; admin `selectProvider(provider)`; `configureFallbacks(orderedList)`.
- **Implementation note:** Built on Spring AI's model abstraction; each provider (OpenAI, Gemini, Claude, Local LLM) is a configured client behind the uniform contract.
- **Data ownership:** `ProviderConfig`, `ProviderSelection`, `ProviderUsageRecord`.
- **Requirements:** 20.

### AI Chat Service

- **Responsibility:** Project-scoped conversational interface. Validates message length (1–10,000 chars), returns/streams responses within latency bounds, retains conversation history and includes the 20 most recent messages as context, resolves Design_Artifact references, handles WebSocket disconnect and provider errors gracefully, and timestamps each message with the submitting User (Req 5).
- **Context window:** The 20-message rolling window per Project is cached in Redis for fast assembly; full history persists in MySQL.
- **Streaming:** First token within 5s over a STOMP WebSocket; on disconnect, partial output is preserved (Req 5.5, 29.2).
- **Data ownership:** `ChatMessage`, `Conversation`.
- **Requirements:** 5, 29.

### Blueprint Service

- **Responsibility:** Assembles all Design_Artifacts into a single versioned Blueprint within 60s of the orchestrator completion signal; rejects assembly naming missing artifacts; assigns sequential per-Project version identifiers starting at 1; retains all prior versions and marks the newest as current; and owns the Approval_Workflow that moves a Project through In_Review → Approved or Changes_Requested, recording approver identity and timestamp, enforcing approval permission, and blocking implementation-phase actions until Approved (Req 18, 19).
- **Data ownership:** `Blueprint`, `BlueprintVersion`, `DesignArtifact`, `ApprovalDecision`, `ChangeRequest`, `Roadmap`, `RoadmapPhase`, `Milestone`.
- **Requirements:** 17, 18, 19.

### Export Service

- **Responsibility:** Renders a Blueprint to PDF and Markdown within 30s, includes the version identifier, watermarks non-Approved exports as draft, enforces export permission, and avoids partial documents on failure (Req 21).
- **Data ownership:** None durable; produces artifacts stored in object storage (S3-compatible) with signed download links.
- **Requirements:** 21.

### Notification Service

- **Responsibility:** Fans out per-agent progress and Project state-change events to authorized Users over WebSocket within 2s of the underlying event; supports the client's reconnect/resync model (Req 22, 29.3). Consumes orchestrator and project events from Kafka and pushes over STOMP topics scoped per Project and per User.
- **Data ownership:** None durable (transient subscription registry; durable event source is Kafka).
- **Requirements:** 22, 29.

### Audit Service

- **Responsibility:** Captures immutable audit events for authentication, role change, Blueprint approval, and Project deletion within 2s; retries failed writes up to 3 times and fails the originating action if all retries fail; retains events ≥365 days; permits Admin-only querying by user/action/time range; and rejects any modification or deletion of audit records (Req 23).
- **Append-only design:** Audit events are written to an append-only store (Kafka topic for ingestion + write-once MySQL table with no UPDATE/DELETE grants for application roles) to enforce immutability at the data layer.
- **Data ownership:** `AuditEvent`.
- **Requirements:** 23.

## AI Agent Architecture

All agents share a common execution contract enforced by the Orchestrator and a common interaction contract enforced by the AI Provider Gateway. Each agent is invoked with the validated outputs of its prerequisites, produces exactly one structured Design_Artifact type, and returns an error indication (rather than a partial artifact) when required inputs are missing.

### Common Agent Contract

- **Input:** `{ projectContext, prerequisiteOutputs[], promptTemplate, providerRequestOptions }`
- **Output:** `AgentOutput { agentId, artifactType, payload (structured), status, producedAt }`
- **Validity rule (Req 6.3):** non-empty and conforms to the artifact's expected structure.
- **Memory model:** Agents are stateless per invocation. "Memory" is the explicit prerequisite outputs passed in plus the persisted `AgentOutput`s of the run — there is no hidden cross-run agent state. This keeps re-queue/retry deterministic (Req 6, 26.6).

### Agent Specifications

| Agent | Goal | Inputs (prerequisites) | Output Artifact | Interacts With | Req |
|---|---|---|---|---|---|
| Requirement_Analyst | Extract classified FRs/NFRs, record assumptions/clarifications | Idea | Requirements list (each with id, statement, classification) | Business_Analyst | 7 |
| Business_Analyst | Stakeholder analysis, value drivers, constraints/assumptions | Idea, requirements | Business analysis | Product_Manager | 8 |
| Product_Manager | User stories (role-feature-benefit), use cases, priorities | Requirements, business analysis | Stories + use cases + priorities | Software_Architect | 9 |
| Software_Architect | Component/microservice decomposition, interactions, event-driven design | Requirements, stories | System architecture | DB/API/Security architects | 10 |
| Database_Architect | Entities, relationships+cardinality, ER design, keys, caching strategy | System architecture | ER + database design | Documentation | 11 |
| API_Architect | Service boundaries, operations, request/response shapes, authN/authZ per boundary | System architecture | API design | Documentation | 13 |
| Security_Architect | AuthN/authZ/data-protection controls, threats+mitigations, encryption, RBAC | System architecture | Security design | DevOps, Documentation | 12 |
| DevOps_Architect | Cloud arch, CI/CD, containerization/orchestration, monitoring/logging/tracing, HA/DR | System + security design | DevOps/cloud architecture | Cost_Estimation, Documentation | 14 |
| Cost_Estimation | Per-category cost ranges with assumptions and currency | System + DevOps architecture | Cost estimate | Documentation | 15 |
| Documentation | Compile all artifacts, TOC, executive summary | All prior outputs | Compiled document | Blueprint assembly | 16 |

### Multi-Agent Workflow (End to End)

```
User starts generation (Project in Generating)
        │
        ▼
Orchestrator creates GenerationRun ──▶ publishes progress: "started"
        │
        ▼  (dependency-ordered, Kafka tasks)
[R7] Requirement_Analyst ─▶ [R8] Business_Analyst ─▶ [R9] Product_Manager ─▶ [R10] Software_Architect
        │                                                                            │
        │                                          ┌─────────────────────────────────┼─────────────────────────────────┐
        │                                          ▼                                 ▼                                 ▼
        │                                   [R11] Database_Architect          [R13] API_Architect            [R12] Security_Architect
        │                                          └─────────────────────────────────┼─────────────────────────────────┘
        │                                                          ┌──────────────────┴──────────────────┐
        │                                                          ▼                                     ▼
        │                                                  [R14] DevOps_Architect                [R15] Cost_Estimation
        │                                                          └──────────────────┬──────────────────┘
        │                                                                             ▼
        │                                                                   [R16] Documentation
        │                                                                             │
        ▼                                                                             ▼
each step: retry≤4, timeout 120s, persist output, publish per-agent progress      all success
        │                                                                             │
   on failure: halt transitive dependents, preserve completed, report failure        ▼
                                                                          Orchestrator signals Blueprint Service
                                                                                      │
                                                                                      ▼
                                                              [R18] Assemble Blueprint (version N) ──▶ Project → In_Review
                                                                                      │
                                                                                      ▼
                                                              [R19] Human review ─▶ Approve → Approved
                                                                                   └▶ Request changes → Changes_Requested
```

## Data Models

Conceptual entities and relationships only (no physical schema, per Req 30). Each service owns its entities; cross-service references are by identifier, not foreign key across databases (database-per-service pattern).

### Domain Entity Map

```
User ──< owns >── Project ──1:1── Idea
 │                   │
 │                   ├──< Requirement >──< UseCase
 │                   ├──< ClarifyingQuestion
 │                   ├──< ProjectStateTransition
 │                   ├──1:1── Conversation ──< ChatMessage
 │                   ├──1:1── GenerationRun ──< AgentInvocation ──1:1── AgentOutput
 │                   └──< Blueprint ──< BlueprintVersion ──< DesignArtifact
 │                                          │
 │                                          ├──1:1── Roadmap ──< RoadmapPhase ──< Milestone
 │                                          ├──< ApprovalDecision
 │                                          └──< ChangeRequest
 │
User >──── Role ──< Permission
User ──< RefreshToken
User ──< OAuthIdentity
User ──< LoginAttempt

ProviderConfig ──< ProviderSelection ; ProviderUsageRecord
AuditEvent (append-only, references User + target id)
```

### Key Entities by Owning Service

| Service | Entities | Notes |
|---|---|---|
| Auth | User, Role, Permission, RefreshToken, OAuthIdentity, LoginAttempt | One role per user (Req 2.1); Guest default (Req 2.2) |
| Project | Project, Idea, Requirement, UseCase, ClarifyingQuestion, ProjectStateTransition | State enum drives lifecycle (Req 3.4) |
| Orchestrator | GenerationRun, AgentInvocation, AgentOutput | Records timing/outcome/attempts (Req 6.7, 6.9) |
| Blueprint | Blueprint, BlueprintVersion, DesignArtifact, Roadmap, RoadmapPhase, Milestone, ApprovalDecision, ChangeRequest | Sequential versioning (Req 18.4–18.5) |
| Chat | Conversation, ChatMessage | 20-msg context window cached in Redis (Req 5.3) |
| AI Provider Gateway | ProviderConfig, ProviderSelection, ProviderUsageRecord | ≥90-day usage retention (Req 20.8) |
| Audit | AuditEvent | Immutable, ≥365-day retention (Req 23) |

### Storage Strategy

- **MySQL (database-per-service):** durable transactional state. Each service owns its schema; no cross-service joins.
- **Redis:** session store, JWT/refresh denylist, chat context windows, rate-limit counters, hot read caches. TTLs align with token and window lifetimes.
- **Kafka:** event backbone — orchestration tasks, progress events, project state events, audit ingestion. Retains an event log enabling replay and re-queue (Req 26.6).
- **Object storage (S3-compatible):** exported PDF/Markdown documents and large artifact blobs.
- **Backups:** automated ≤24h interval, ≥30-day retention, point-in-time restore (Req 28).

## Security Design

### Authentication and Token Strategy (Req 1)

- **Access token:** Short-lived JWT (15-min TTL), signed (asymmetric keys so services verify without sharing secrets), carrying subject, single role, and standard claims. Verified at the Gateway and re-verified at each service.
- **Refresh token:** Opaque, stored hashed, 7-day TTL, single-use with rotation on each refresh. Logout, role change, and lockout revoke active refresh tokens.
- **OAuth2:** Authorization-code flow; on success the Platform issues its own access/refresh tokens, mapping the external identity to a `User` via `OAuthIdentity`. Exchange failures issue no tokens (Req 1.13).
- **Lockout:** 5 failed attempts in a rolling 15-minute window locks the account for 15 minutes; attempts during lockout are rejected without credential evaluation (Req 1.11, 1.14).
- **Registration validation:** email 1–254 chars, password 12–128 chars with mixed character classes; uniform errors that never reveal which field was wrong on login (Req 1.9, 1.12).

### Authorization and RBAC (Req 2)

- Single role per user from {Admin, Architect, Product_Manager_Role, Developer, Client, Guest}; Guest is the default.
- Permission checks are centralized in the Auth Service's authorization domain and return permit/deny within 500 ms. Denials change no state and return a clear authorization error.
- Role changes invalidate permissions cached under the prior role and take effect within 5 seconds (Req 2.13–2.14).

| Capability | Admin | Architect | Product_Manager_Role | Developer | Client | Guest |
|---|---|---|---|---|---|---|
| User/role/config admin | ✔ | | | | | |
| Create/edit/approve Design_Artifacts & Blueprints | | ✔ | | | | |
| Create/manage Projects, requirements, stories | | | ✔ | | | |
| Read approved Blueprints / submit impl. feedback | ✔ | ✔ | ✔ | ✔ | | |
| Submit Ideas / business-level approval | | | | | ✔ | |
| Read-only demo content | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

(Approval permission for the Blueprint approval gate is held by roles authorized to approve — Architect for technical approval and Client for business approval — enforced by Req 19.2/19.5.)

### API Security

- All traffic over TLS; unencrypted connections rejected (Req 25.1–25.2).
- Edge rate limiting at 100 req/min per authenticated client per window with retry-after, plus per-service input validation (type, required fields, ≤1 MB payload) before processing (Req 25.3–25.7).
- Validation errors never leak stack traces, file paths, or DB structure (Req 25.7).

### Encryption and Data Protection

- **In transit:** TLS everywhere, including service-to-service.
- **At rest:** Sensitive data (credentials, PII, financial records) encrypted at rest (Req 25.3). Passwords stored with a strong adaptive hash; refresh tokens stored hashed.

### Audit and Non-Repudiation

- Immutable, append-only audit trail for security- and change-relevant events with ≥365-day retention; Admin-only query; modification/deletion rejected at the data layer (Req 23).

### Threat Considerations

| Threat | Mitigation |
|---|---|
| Credential stuffing / brute force | Lockout (Req 1.11), rate limiting (Req 25.4) |
| Token theft / replay | Short access TTL, refresh rotation + revocation, denylist in Redis |
| Privilege escalation | Centralized RBAC, role-change cache invalidation (Req 2.14) |
| Prompt injection via Idea/chat into agents | Treat user content as untrusted data in prompts; structured output validation (Req 6.3); no tool/command execution from agent output |
| Provider data exposure | Provider abstraction limits payloads; usage records avoid storing secret content; Local LLM option for sensitive workloads (Req 20) |
| Audit tampering | Append-only store, no UPDATE/DELETE grants (Req 23.7) |

## Scalability and Event-Driven Design

- **Stateless services + Redis shared state** allow any instance to serve any request and enable horizontal scaling (Req 26.2–26.3).
- **Kafka work queue** decouples request acceptance from long-running agent work; submissions are acknowledged within 2s and processed asynchronously (Req 26.1). If Kafka is unavailable, submissions are rejected with retained request data for resubmission (Req 26.5).
- **Load distribution** keeps any instance within 20% of mean workload (Req 26.2); under 100 concurrent generations, new Project creation still responds within 5s (Req 26.4).
- **Failure recovery:** incomplete work from a failed instance is re-queued within 30s (Req 26.6), made safe by persisted per-step outputs.

## DevOps and Deployment Design

### CI/CD (GitHub Actions)

```
push / PR ─▶ build (Maven, Java 21) ─▶ unit tests ─▶ static analysis + dependency scan
        ─▶ container build (Docker) ─▶ integration tests ─▶ push image to registry
        ─▶ deploy to staging (K8s) ─▶ smoke/e2e ─▶ manual gate ─▶ deploy to prod (K8s)
```

### Containerization and Orchestration

- Each service is a Docker image; deployed to Kubernetes with per-service Deployments, HorizontalPodAutoscalers, liveness/readiness probes (Req 28.7), and ConfigMaps/Secrets sourced from Spring Cloud Config and the cluster secret store.
- Backing services on AWS-ready managed equivalents: RDS (MySQL), ElastiCache (Redis), MSK (Kafka), S3 (exports), EKS (compute).

### Observability (Req 27)

- **Metrics:** Each service exposes Prometheus metrics (request count, error count, latency); Grafana dashboards; values fresh within 60s.
- **Logging:** Structured logs (timestamp, severity, correlation id, message) shipped to ELK.
- **Tracing:** Zipkin spans with propagated correlation id across all services; missing inbound ids are generated at the edge (Req 27.5–27.6).
- **Telemetry resilience:** when the Observability stack is unreachable, services keep serving and retry emission up to 3 times (Req 27.7).

### Production Readiness, HA, and DR (Req 28)

- Automated backups ≤24h, ≥30-day retention, recorded and selectable; restore within 60 minutes with pre-restore data preserved on failure.
- Health checks every ≤30s (≤10s each); 3 consecutive failures mark an instance unhealthy; traffic reroutes within 30s.
- Multi-AZ deployment posture for stateful managed services; rolling deployments for zero-downtime releases.

## Error Handling

| Scenario | Handling | Req |
|---|---|---|
| Invalid credentials / registration | Reject, uniform/validation error, no account or token created | 1.2, 1.9, 1.12 |
| Unauthorized action | Deny within 500 ms, no state change, authorization error | 2.5 |
| Invalid project state transition | Reject, preserve state, transition error | 3.10 |
| AI provider failure during analysis | Retry ≤3, preserve state, error indication | 4.9 |
| Chat provider error/timeout | Fallback message, preserve user message | 5.6 |
| Agent invalid output / timeout | Retry ≤4 total, 120s timeout, halt dependents, preserve completed | 6.4–6.6 |
| Missing artifact at assembly | Reject, name missing artifacts, no partial Blueprint | 18.3 |
| Approval by unauthorized user | Deny, preserve state, authorization error | 19.5 |
| Provider unavailable, no fallback | Provider-unavailable error, input unchanged | 20.7 |
| Export without permission / failure | Reject or no partial document, error indication | 21.2, 21.6 |
| Audit write failure | Retry ≤3, then fail originating action, preserve state | 23.2 |
| Kafka unavailable on submit | Reject, retain request for resubmission | 26.5 |
| WebSocket lost | Show last status + indicator, reconnect ≤10 attempts, resync on reconnect | 22.3–22.6 |

**Cross-cutting principles:** fail closed on authorization and approval; never emit partial artifacts where a complete-or-error contract is specified; surface user-safe error messages while logging detailed diagnostics with the correlation id.

## Frontend Design

### Pages and Navigation

```
/auth                 Sign-in / Register (Req 24.1)
/dashboard            Role-scoped dashboard of authorized Projects (Req 24.2)
/projects/:id         Project workspace: Idea, requirements, chat, artifacts, live progress (Req 24.3–24.4)
/projects/:id/review  Blueprint review: all Design_Artifacts + approval controls (Req 24.5)
/admin                Admin only: users, roles, AI provider config (Req 24.6, 24.8)
```

- **State management:** Redux Toolkit for app/session/role state; React Query for server cache, fetching, and mutations; MUI + Tailwind for components and layout.
- **Real-time:** STOMP-over-WebSocket client subscribes to per-Project progress and state-change topics; renders per-agent progress and reconnect/resync UX (Req 22, 24.4).
- **Authorization-aware rendering:** only controls permitted for the current role are rendered; others omitted (Req 24.7). Non-Admins attempting `/admin` see a permission error (Req 24.8). Page-load failures time out at 10s with an error state (Req 24.9).

### Key Components (conceptual)

| Component | Purpose |
|---|---|
| AuthForms | Sign-in/registration with client-side validation mirroring Req 1 rules |
| ProjectDashboard | Lists authorized Projects with state badges |
| IdeaPanel / RequirementsEditor | View/edit Idea and structured requirements (Req 4.7) |
| ChatPanel | Streaming conversational UI with artifact references (Req 5) |
| GenerationProgress | Per-agent progress timeline driven by WebSocket events |
| BlueprintViewer | Renders all Design_Artifacts; export controls |
| ApprovalControls | Approve / request changes (permission-gated) (Req 19) |
| AdminConsole | User management, role assignment, provider selection/fallback config |

## Testing Strategy

| Level | Scope | Examples |
|---|---|---|
| Unit | Service logic, state machine, RBAC decisions, validators | Project state transition rules (Req 3.9–3.10); token TTLs (Req 1.4–1.5); rate-limit window reset (Req 25.5) |
| Integration | Service + MySQL/Redis/Kafka via test containers | Orchestrator retry/halt; refresh rotation; audit immutability (Req 23.7) |
| Contract | AI Provider Gateway uniform contract + provider stubs | Failover ordering and provider-unavailable error (Req 20.5–20.7) |
| End-to-end | Full generation run on a sample Idea | Idea → agents → assembly → review → approve → export |
| Performance | Latency/throughput under load | 95th-percentile bounds at 500 concurrent users (Req 29); 100 concurrent generations (Req 26.4) |
| Security | AuthN/authZ, rate limit, input validation, prompt-injection resistance | Lockout (Req 1.11); validation leakage (Req 25.7) |
| Resilience | Failure injection | Kafka outage (Req 26.5); instance loss re-queue (Req 26.6); telemetry outage (Req 27.7) |

**Approach:** test-first for the orchestration saga, state machines, RBAC, and the approval gate, since these encode the system's hardest invariants. AI agent outputs are validated against structural schemas rather than exact text, and providers are stubbed in tests for determinism.

## Requirements Traceability

| Requirement | Primary Design Element(s) |
|---|---|
| 1 Authentication | Auth Service (token strategy, OAuth2, lockout) |
| 2 Authorization | Auth Service authorization domain, RBAC matrix |
| 3 Project lifecycle | Project Service state machine |
| 4 Requirement analysis | Requirement Analysis Module + Provider Gateway |
| 5 Chat | AI Chat Service, Redis context window, WebSocket streaming |
| 6 Orchestration | Orchestrator Service saga, agent DAG, Kafka tasks |
| 7–16 Agents | Agent Workers + common agent contract |
| 17 Roadmap | Blueprint Service (Roadmap/Phase/Milestone) |
| 18 Assembly | Blueprint Service assembly + versioning |
| 19 Approval gate | Blueprint Service Approval_Workflow |
| 20 Provider abstraction | AI Provider Gateway (selection, failover, usage) |
| 21 Export | Export Service (PDF/MD, watermark, versioning) |
| 22 Notifications | Notification Service, WebSocket fan-out |
| 23 Audit | Audit Service append-only store |
| 24 Web pages | Frontend pages/components, authZ-aware rendering |
| 25 Security hardening | Gateway TLS/rate limit + per-service validation/encryption |
| 26 Scalability | Stateless services, Redis state, Kafka queue |
| 27 Observability | Prometheus/Grafana, ELK, Zipkin, correlation id |
| 28 Production readiness | Backups, health checks, HA/DR, K8s probes |
| 29 Performance | Latency budgets, streaming, WebSocket delivery |
| 30 Tech stack constraint | Technology Stack Mapping table; design-only scope |

## Open Design Questions (for review)

1. **Approval authority split:** Should technical approval (Architect) and business approval (Client) be two distinct gates, or a single approval sufficient to reach `Approved`? Current design treats either authorized role as able to approve; a dual-sign-off variant is possible.
2. **Generation cost controls:** Should the Platform enforce per-Project or per-tenant token/cost budgets on agent runs to cap provider spend? Not currently a requirement.
3. **Multi-tenancy boundary:** Single-tenant per deployment vs. shared multi-tenant with tenant isolation in data and rate limits — affects RBAC scoping and backup granularity.
4. **Local LLM quality fallback:** When failover lands on a Local LLM, output quality may differ; should the run be flagged for re-review?
5. **Artifact regeneration granularity:** On `Changes_Requested`, should regeneration re-run the full agent DAG or only the affected downstream agents? Partial re-run is more efficient but adds dependency-tracking complexity.

These do not block design approval; they are decisions to confirm before or during implementation planning.

## Correctness Properties

These are invariants the system must uphold for any input or sequence of operations. They are candidates for property-based and invariant testing.

### Property 1: Access token lifetime
Any issued access token has an expiry exactly 15 minutes after issuance; no operation extends an existing access token's lifetime.
**Validates: Requirements 1.4**

### Property 2: Refresh single-use
A refresh token, once exchanged, is never accepted again; each refresh yields a new refresh token and invalidates the prior one.
**Validates: Requirements 1.6, 1.7**

### Property 3: Single role
At all times every User has exactly one role from the defined set.
**Validates: Requirements 2.1**

### Property 4: Deny is side-effect-free
A denied action never changes any state associated with that action.
**Validates: Requirements 2.5**

### Property 5: No credential leak
A failed login response is indistinguishable with respect to which field (email or password) was wrong.
**Validates: Requirements 1.9**

### Property 6: Single project state
A Project is in exactly one state at any time.
**Validates: Requirements 3.4**

### Property 7: Legal transitions only
Every executed state change is a member of the permitted-transition set; rejected transitions leave the state unchanged.
**Validates: Requirements 3.9, 3.10**

### Property 8: Transition audit completeness
Every state change produces exactly one transition record with timestamp and initiating User.
**Validates: Requirements 3.8**

### Property 9: Agent dependency order
No agent is invoked before all its prerequisites have valid outputs.
**Validates: Requirements 6.1**

### Property 10: Bounded agent attempts
No agent is invoked more than 4 times total within a single generation run.
**Validates: Requirements 6.5**

### Property 11: Failure isolation
When a step fails, all and only its transitive dependents are halted; previously valid outputs are retained.
**Validates: Requirements 6.6**

### Property 12: Complete-or-error agent output
Each agent returns either a structurally valid artifact or an error indication, never a partial artifact.
**Validates: Requirements 7.1, 8.4, 9.4, 10.5, 11.6, 12.6, 13.5, 14.6, 15.5, 16.4**

### Property 13: Monotonic versioning
Blueprint version identifiers within a Project are unique and strictly increasing, starting at 1; prior versions are never mutated.
**Validates: Requirements 18.4, 18.5**

### Property 14: Assembly completeness
A Blueprint is assembled only if every required Design_Artifact is present; otherwise no Blueprint is created.
**Validates: Requirements 18.3**

### Property 15: Approval gate
No implementation-phase action executes for a Project unless it is in the `Approved` state.
**Validates: Requirements 19.6**

### Property 16: Authorized approval only
A transition to `Approved` occurs only following an explicit decision by a User holding approval permission while the Project is `In_Review`.
**Validates: Requirements 19.2, 19.5, 19.7**

### Property 17: Uniform provider contract
The request/response field set presented to agents does not vary by provider.
**Validates: Requirements 20.4**

### Property 18: Failover order
Failover visits configured fallback providers in priority order and stops at the first success or after exhausting fallbacks.
**Validates: Requirements 20.6, 20.7**

### Property 19: Usage record completeness
Every routed request records the serving provider and timestamp.
**Validates: Requirements 20.8**

### Property 20: Audit immutability
No operation by any role modifies or deletes a recorded audit event.
**Validates: Requirements 23.7**

### Property 21: Audit-or-abort
A security-relevant action either has its audit event durably recorded or the action itself is rejected.
**Validates: Requirements 23.2**

### Property 22: Correlation propagation
Every request handled across services carries one unchanged correlation identifier end to end.
**Validates: Requirements 27.5, 27.6**

### Property 23: Work conservation
Submitted generation work is either acknowledged and eventually processed (possibly after re-queue) or rejected with retained request data; it is never silently lost.
**Validates: Requirements 26.1, 26.5, 26.6**
