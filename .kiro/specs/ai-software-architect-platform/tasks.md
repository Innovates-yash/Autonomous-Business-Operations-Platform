# Implementation Plan: AI Software Architect Platform

## Overview

This plan converts the design into incremental, dependency-ordered coding tasks. Implementation uses the constrained stack: Java 21 + Spring Boot 3 (Spring Cloud, Spring Security, Spring AI, Spring Data JPA) for backend services, React + TypeScript + Vite for the Web_Client, and MySQL/Redis/Kafka for data, with Docker/Kubernetes/GitHub Actions for delivery and Prometheus/Grafana/ELK/Zipkin for observability.

Top-level tasks are epics; decimal sub-tasks are the executable units. Test sub-tasks are marked optional with `*`. Property-based test sub-tasks reference a specific Correctness Property from the design and the requirements clause it validates. Checkpoints appear at natural integration boundaries.

## Tasks

- [x] 1. Project scaffolding and infrastructure
  - [x] 1.1 Initialize multi-module Maven project and shared commons
    - Create Java 21 / Spring Boot 3 parent POM with modules: api-gateway, auth-service, project-service, orchestrator-service, ai-provider-gateway, ai-chat-service, blueprint-service, export-service, notification-service, audit-service, agent-workers, commons
    - Add shared commons module (correlation-id model, error contracts, common DTOs)
    - _Requirements: 30_
  - [x] 1.2 Create local infrastructure and per-service configuration
    - Author Docker Compose for MySQL, Redis, Kafka, Prometheus, Grafana, ELK, Zipkin
    - Add Dockerfiles per service and application.yml with dev/staging/prod profiles
    - _Requirements: 30, 26, 28_
  - [x] 1.3 Set up GitHub Actions CI pipeline
    - Configure build (Maven, Java 21), unit tests, static analysis + dependency scan, container build, image push
    - _Requirements: 30_

- [ ] 2. API Gateway service
  - [x] 2.1 Implement Spring Cloud Gateway routing and TLS enforcement
    - Define routes to downstream services; terminate TLS; reject unencrypted connections; WebSocket upgrade passthrough for `/ws/*`
    - _Requirements: 25.1, 25.2, 27.1_
  - [x] 2.2 Implement JWT validation and correlation-id filters
    - Validate access tokens at the edge; generate correlation id when absent and propagate `X-Correlation-Id` downstream
    - _Requirements: 27.5, 27.6_
  - [x] 2.3 Implement Redis-backed edge rate limiting
    - Enforce 100 req/min per authenticated client with 60s window reset and Retry-After header
    - _Requirements: 25.4, 25.5_
  - [ ]* 2.4 Write property test for correlation propagation
    - **Property 22: Correlation propagation**
    - **Validates: Requirements 27.5, 27.6**
  - [ ]* 2.5 Write unit tests for rate limit window and unencrypted rejection
    - Test 100-req threshold, window reset, and rejection path
    - _Requirements: 25.2, 25.4, 25.5_

- [ ] 3. Auth Service — registration, login, tokens
  - [x] 3.1 Create auth entities and Flyway migration
    - Define User, Role, Permission, RefreshToken, LoginAttempt, OAuthIdentity
    - _Requirements: 1.1, 2.1_
  - [x] 3.2 Implement registration with validation
    - Email 1–254 chars, password 12–128 chars mixed classes, duplicate rejection, field-level validation errors, ≤5s
    - _Requirements: 1.1, 1.2, 1.12_
  - [x] 3.3 Implement login, JWT/refresh issuance, and refresh rotation
    - JWT 15-min TTL, refresh 7-day TTL single-use rotation, uniform login error, logout invalidation, ≤5s
    - _Requirements: 1.3, 1.4, 1.5, 1.6, 1.7, 1.9, 1.10_
  - [x] 3.4 Implement account lockout
    - Lock after 5 failed attempts in rolling 15-min window; reject attempts during lockout without credential evaluation
    - _Requirements: 1.11, 1.14_
  - [ ]* 3.5 Write property test for access token lifetime
    - **Property 1: Access token lifetime**
    - **Validates: Requirements 1.4**
  - [ ]* 3.6 Write property test for refresh single-use
    - **Property 2: Refresh single-use**
    - **Validates: Requirements 1.6, 1.7**
  - [ ]* 3.7 Write property test for no credential leak
    - **Property 5: No credential leak**
    - **Validates: Requirements 1.9**
  - [ ]* 3.8 Write unit tests for lockout and validation
    - Test lockout window, complexity rules, duplicate-account path
    - _Requirements: 1.2, 1.11, 1.12, 1.14_

- [ ] 4. Auth Service — OAuth2 and authorization
  - [x] 4.1 Implement OAuth2 authorization-code flow
    - 10s issuance on success, error with no tokens on exchange failure/denial
    - _Requirements: 1.8, 1.13_
  - [x] 4.2 Implement role assignment and RBAC matrix
    - Single role per user, Guest default, Admin-only assignment, prior-role cache invalidation, effect within 5s
    - _Requirements: 2.1, 2.2, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13, 2.14_
  - [x] 4.3 Implement authorization decision endpoint
    - permit/deny within 500ms, no state change on deny, no-role denial path
    - _Requirements: 2.3, 2.4, 2.5, 2.6_
  - [ ]* 4.4 Write property test for single role
    - **Property 3: Single role**
    - **Validates: Requirements 2.1**
  - [ ]* 4.5 Write property test for side-effect-free deny
    - **Property 4: Deny is side-effect-free**
    - **Validates: Requirements 2.5**
  - [ ]* 4.6 Write unit tests for RBAC matrix and cache invalidation
    - Test per-role permissions and 5s role-change propagation
    - _Requirements: 2.7, 2.13, 2.14_

- [ ] 5. Checkpoint — auth and gateway
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Project Service — CRUD and state machine
  - [x] 6.1 Create project entities
    - Define Project, Idea, Requirement, UseCase, ClarifyingQuestion, ProjectStateTransition
    - _Requirements: 3.1, 3.4_
  - [x] 6.2 Implement project CRUD with validation and scoping
    - Create (name 1–200, desc 1–5000, Draft, owner), update, authorized list, get-or-404, field validation
    - _Requirements: 3.1, 3.2, 3.3, 3.6, 3.7, 3.11_
  - [x] 6.3 Implement state machine and transition recording
    - Enforce permitted transitions, reject invalid with state preserved, record each transition with timestamp + user, archive with retention
    - _Requirements: 3.4, 3.5, 3.8, 3.9, 3.10_
  - [ ]* 6.4 Write property test for single project state
    - **Property 6: Single project state**
    - **Validates: Requirements 3.4**
  - [ ]* 6.5 Write property test for legal transitions only
    - **Property 7: Legal transitions only**
    - **Validates: Requirements 3.9, 3.10**
  - [ ]* 6.6 Write property test for transition audit completeness
    - **Property 8: Transition audit completeness**
    - **Validates: Requirements 3.8**

- [ ] 7. AI Provider Gateway service
  - [x] 7.1 Create provider entities and uniform contract
    - Define ProviderConfig, ProviderSelection, ProviderUsageRecord; implement uniform complete/stream contract via Spring AI
    - _Requirements: 20.1, 20.4_
  - [x] 7.2 Implement provider selection and provider clients
    - Admin selection with 5s activation, reject unconfigured (retain prior); configure OpenAI/Gemini/Claude/Local LLM clients and stubs
    - _Requirements: 20.1, 20.2, 20.3_
  - [x] 7.3 Implement unavailability detection, failover, and usage recording
    - Timeout (1–120s, default 30s) + 3 consecutive errors → unavailable; fail over up to 3 fallbacks in priority; provider-unavailable error preserving input; record provider+timestamp with ≥90-day retention
    - _Requirements: 20.5, 20.6, 20.7, 20.8_
  - [ ]* 7.4 Write property test for uniform provider contract
    - **Property 17: Uniform provider contract**
    - **Validates: Requirements 20.4**
  - [ ]* 7.5 Write property test for failover order
    - **Property 18: Failover order**
    - **Validates: Requirements 20.6, 20.7**
  - [ ]* 7.6 Write property test for usage record completeness
    - **Property 19: Usage record completeness**
    - **Validates: Requirements 20.8**

- [ ] 8. Project Service — requirement analysis module
  - [x] 8.1 Implement analysis initiation and generation
    - AI call producing ≥1 FR + ≥1 NFR within 60s, transition to Analyzing, use cases with traceability
    - _Requirements: 4.1, 4.2, 4.5, 4.6_
  - [ ] 8.2 Implement clarifying questions, answer incorporation, and manual edits
    - 1–10 questions with references; regenerate affected requirements on answers; add/modify/remove while Analyzing; confirm transition
    - _Requirements: 4.3, 4.4, 4.7, 4.8_
  - [ ] 8.3 Implement provider failure retry with state preservation
    - Retry up to 3 times; on exhaustion halt, preserve prior state and requirements, return provider-failure error
    - _Requirements: 4.9_
  - [ ]* 8.4 Write unit tests for analysis edge cases
    - Test min FR/NFR output, question bounds, retry exhaustion
    - _Requirements: 4.1, 4.3, 4.9_

- [ ] 9. Orchestrator Service — saga engine
  - [x] 9.1 Create orchestration entities and dependency DAG
    - Define GenerationRun, AgentInvocation, AgentOutput; encode the ten-agent dependency DAG
    - _Requirements: 6.1, 6.2_
  - [ ] 9.2 Implement Kafka saga engine with concurrency
    - Dependency-ordered invocation, prerequisite output passing, concurrent execution for independent agents, persist outputs for safe re-queue
    - _Requirements: 6.1, 6.2, 6.10_
  - [ ] 9.3 Implement timeout, retry, halt, and recording
    - 120s timeout, 4-attempt max, halt transitive dependents on failure while preserving outputs, record timing/outcome, publish progress events, signal assembly on completion
    - _Requirements: 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9_
  - [ ]* 9.4 Write property test for agent dependency order
    - **Property 9: Agent dependency order**
    - **Validates: Requirements 6.1**
  - [ ]* 9.5 Write property test for bounded agent attempts
    - **Property 10: Bounded agent attempts**
    - **Validates: Requirements 6.5**
  - [ ]* 9.6 Write property test for failure isolation
    - **Property 11: Failure isolation**
    - **Validates: Requirements 6.6**

- [ ] 10. Checkpoint — core services and orchestration
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 11. Agent Workers — common framework and Requirement Analyst
  - [ ] 11.1 Implement common agent worker framework
    - Kafka consumer: read task, fetch prerequisites, call Provider Gateway, validate output structure, persist AgentOutput, acknowledge; complete-or-error contract
    - _Requirements: 6.3, 7.5_
  - [ ] 11.2 Implement Requirement_Analyst_Agent
    - Idea → requirements list with unique ids, functional/non-functional classification, assumptions, clarifying questions; error on empty/unprocessable input
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  - [ ]* 11.3 Write property test for complete-or-error agent output
    - **Property 12: Complete-or-error agent output**
    - **Validates: Requirements 7.1, 8.4, 9.4, 10.5, 11.6, 12.6, 13.5, 14.6, 15.5, 16.4**

- [ ] 12. Agent Workers — Business Analyst, Product Manager, Software Architect
  - [ ] 12.1 Implement Business_Analyst_Agent
    - Stakeholders with role+interest, value drivers linked to stakeholders, constraints/assumptions with traceability, missing-input error, 3-attempt failure handling
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_
  - [ ] 12.2 Implement Product_Manager_Agent
    - User stories in role-feature-benefit per FR, use cases mapped to stories, one priority per story, error on no requirements
    - _Requirements: 9.1, 9.2, 9.3, 9.4_
  - [ ] 12.3 Implement Software_Architect_Agent
    - Components + microservices with responsibilities, interactions with sync/async style, event-driven description, completeness check, missing-input error
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  - [ ]* 12.4 Write unit tests for BA/PM/Architect output structure
    - Test traceability, story-to-use-case mapping, microservice completeness rule
    - _Requirements: 8.3, 9.2, 10.6_

- [ ] 13. Agent Workers — Database, Security, API Architects
  - [ ] 13.1 Implement Database_Architect_Agent
    - Entities, relationships with cardinality, ER design, key attributes, Redis caching strategy, undetermined-cardinality handling, empty-input result
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_
  - [ ] 13.2 Implement Security_Architect_Agent
    - AuthN/authZ/data-protection sections, threats+mitigations, at-rest and in-transit encryption, RBAC model, ≤120s, missing-input error
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_
  - [ ] 13.3 Implement API_Architect_Agent
    - Service boundaries, ≥1 operation each, request/response field shapes, authN/authZ per boundary, missing-info error
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5_
  - [ ]* 13.4 Write unit tests for DB/Security/API agent outputs
    - Test cardinality enumeration, encryption sections, per-boundary auth requirements
    - _Requirements: 11.2, 12.3, 12.4, 13.4_

- [ ] 14. Agent Workers — DevOps, Cost Estimation, Documentation
  - [ ] 14.1 Implement DevOps_Architect_Agent
    - Cloud arch, CI/CD stages, containerization/orchestration, monitoring/logging/tracing, HA/DR with RTO/RPO, missing-input and incomplete-artifact errors
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7_
  - [ ] 14.2 Implement Cost_Estimation_Agent
    - Per-category costs + summed total, assumptions (volume/region/period), numeric ranges with single currency, partial estimation, error on no estimate
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_
  - [ ] 14.3 Implement Documentation_Agent
    - Compile all artifacts as distinct titled sections once, TOC matching section order, executive summary 100–1000 words, missing-output error, 3-attempt failure handling
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5_
  - [ ]* 14.4 Write unit tests for DevOps/Cost/Documentation outputs
    - Test RTO/RPO presence, total-equals-sum, TOC/section parity, summary word bounds
    - _Requirements: 14.5, 15.1, 16.2, 16.3_

- [ ] 15. Checkpoint — agent workers
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 16. Blueprint Service — assembly, versioning, and roadmap
  - [ ] 16.1 Create blueprint entities
    - Define Blueprint, BlueprintVersion, DesignArtifact, Roadmap, RoadmapPhase, Milestone, ApprovalDecision, ChangeRequest
    - _Requirements: 18.1, 17.1_
  - [ ] 16.2 Implement assembly and versioning
    - Assemble within 60s, reject naming missing artifacts (no partial), sequential versioning from 1, retain priors and mark current, record timestamp+version, transition to In_Review
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6_
  - [ ] 16.3 Implement roadmap generation
    - 2–20 ordered phases, deliverables per phase, ≥1 milestone per phase, forward-only dependencies, full coverage, cycle detection
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6_
  - [ ]* 16.4 Write property test for monotonic versioning
    - **Property 13: Monotonic versioning**
    - **Validates: Requirements 18.4, 18.5**
  - [ ]* 16.5 Write property test for assembly completeness
    - **Property 14: Assembly completeness**
    - **Validates: Requirements 18.3**
  - [ ]* 16.6 Write unit tests for roadmap constraints
    - Test phase bounds, forward-only dependencies, cycle detection
    - _Requirements: 17.1, 17.4, 17.6_

- [ ] 17. Blueprint Service — approval workflow
  - [ ] 17.1 Implement review availability and approval
    - Make Blueprint available within 5s of assembly; approve with permission check, transition to Approved, record approver+timestamp
    - _Requirements: 19.1, 19.2, 19.3_
  - [ ] 17.2 Implement request-changes and gating
    - Record change request, retain Blueprint, transition to Changes_Requested (enables regeneration); deny unauthorized approval; reject approval when not In_Review; block implementation actions until Approved
    - _Requirements: 19.4, 19.5, 19.6, 19.7_
  - [ ]* 17.3 Write property test for approval gate
    - **Property 15: Approval gate**
    - **Validates: Requirements 19.6**
  - [ ]* 17.4 Write property test for authorized approval only
    - **Property 16: Authorized approval only**
    - **Validates: Requirements 19.2, 19.5, 19.7**

- [ ] 18. AI Chat Service
  - [ ] 18.1 Create chat entities and message endpoint
    - Define Conversation, ChatMessage; validate 1–10,000 chars (reject empty/over-limit preserving input), associate user id + UTC timestamp
    - _Requirements: 5.1, 5.2, 5.9_
  - [ ] 18.2 Implement context window and streaming
    - 20-message Redis context window, STOMP streaming with first token ≤5s and total ≤30s, preserve partial on disconnect
    - _Requirements: 5.1, 5.3, 5.4, 5.5_
  - [ ] 18.3 Implement artifact references and provider error handling
    - Include referenced artifact if present; error+continue if missing; fallback message on provider error preserving input
    - _Requirements: 5.6, 5.7, 5.8_
  - [ ]* 18.4 Write unit tests for chat validation and context window
    - Test length bounds, 20-message window, disconnect preservation
    - _Requirements: 5.2, 5.3, 5.5_

- [ ] 19. Notification Service
  - [ ] 19.1 Implement Kafka consumers and STOMP broker
    - Consume progress and state-change events; STOMP topics scoped per Project and per User with subscription authorization
    - _Requirements: 22.1, 22.2_
  - [ ] 19.2 Implement delivery and reconnect resync
    - Deliver progress and state-change within 2s; on reconnection deliver current status within 2s
    - _Requirements: 22.1, 22.2, 22.6_
  - [ ]* 19.3 Write unit tests for subscription authorization and resync
    - Test per-project/per-user scoping and reconnect status delivery
    - _Requirements: 22.2, 22.6_

- [ ] 20. Audit Service
  - [x] 20.1 Create append-only audit store
    - Define AuditEvent with no UPDATE/DELETE grants; Kafka ingestion + write-once table
    - _Requirements: 23.1, 23.7_
  - [x] 20.2 Implement event recording and retry-or-abort
    - Record within 2s (user, action, target, UTC ms timestamp); retry 3x and reject originating action on exhaustion; retain ≥365 days
    - _Requirements: 23.1, 23.2, 23.3_
  - [x] 20.3 Implement Admin-only query
    - Filter by user/action/time within 5s, empty set on no match, deny non-Admin, reject modification/deletion attempts
    - _Requirements: 23.4, 23.5, 23.6, 23.7_
  - [ ]* 20.4 Write property test for audit immutability
    - **Property 20: Audit immutability**
    - **Validates: Requirements 23.7**
  - [ ]* 20.5 Write property test for audit-or-abort
    - **Property 21: Audit-or-abort**
    - **Validates: Requirements 23.2**

- [ ] 21. Export Service
  - [ ] 21.1 Implement export with permission and rendering
    - Enforce export permission (reject unauthorized); render PDF and Markdown with version id within 30s; draft watermark for non-Approved; no partial document on failure; store in S3-compatible storage with signed URLs
    - _Requirements: 21.1, 21.2, 21.3, 21.4, 21.5, 21.6_
  - [ ]* 21.2 Write unit tests for export gating and watermark
    - Test permission denial, watermark on non-Approved, failure-no-partial
    - _Requirements: 21.2, 21.4, 21.6_

- [ ] 22. Checkpoint — blueprint, chat, notifications, audit, export
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 23. Frontend — authentication and shell
  - [ ] 23.1 Initialize frontend project
    - React + TS + Vite + Tailwind + MUI + Redux Toolkit + React Query
    - _Requirements: 24.1_
  - [ ] 23.2 Implement auth pages and token management
    - Sign-in/register with client-side validation, in-memory JWT with auto-refresh, logout, OAuth2 flow
    - _Requirements: 24.1, 1.1, 1.3, 1.8_
  - [ ] 23.3 Implement app shell with authorization-aware rendering
    - Role-aware nav, route guards, omit unpermitted controls, admin permission error, 10s page-load timeout
    - _Requirements: 24.7, 24.8, 24.9_
  - [ ]* 23.4 Write unit tests for auth-aware rendering and timeout
    - Test control omission by role and 10s load timeout
    - _Requirements: 24.7, 24.9_

- [ ] 24. Frontend — dashboard and project workspace
  - [ ] 24.1 Implement role-scoped dashboard
    - Authorized Projects with state badges, render ≤3s
    - _Requirements: 24.2_
  - [ ] 24.2 Implement project workspace panels
    - IdeaPanel, RequirementsEditor (add/modify/remove + questions), ChatPanel (streaming, validation, disconnect indicator), render ≤3s with view permission
    - _Requirements: 24.3, 5.1, 5.2, 4.7, 4.8_
  - [ ] 24.3 Implement generation progress and reconnect UX
    - Per-agent timeline updating ≤3s on events; WebSocket reconnect ≤5s intervals up to 10 attempts, error on exhaustion, resync on reconnect
    - _Requirements: 24.4, 22.3, 22.4, 22.5, 22.6_

- [ ] 25. Frontend — review, export, and admin
  - [ ] 25.1 Implement blueprint review and export controls
    - Review page with all artifacts + approval controls + roadmap; PDF/Markdown trigger with download link
    - _Requirements: 24.5, 19.2, 19.4, 21.1, 21.3_
  - [ ] 25.2 Implement admin console
    - User management, role assignment, AI provider selection and fallback configuration
    - _Requirements: 24.6, 2.7, 20.2_

- [ ] 26. Security hardening — validation and encryption
  - [x] 26.1 Implement global input validation and size limit
    - 1 MB request size limit, per-service Bean Validation (type, required fields), safe error messages without internal details
    - _Requirements: 25.6, 25.7_
  - [x] 26.2 Implement encryption and credential protection
    - At-rest encryption for sensitive data, inter-service mTLS, bcrypt password storage, SHA-256 refresh token hashing
    - _Requirements: 25.1, 25.3_
  - [ ]* 26.3 Write unit tests for validation safety and size limit
    - Test no-leak error messages and 1 MB rejection
    - _Requirements: 25.6, 25.7_

- [ ] 27. Observability — metrics, logging, tracing
  - [x] 27.1 Configure metrics and structured logging
    - Micrometer + Prometheus (request count, errors, latency, ≤60s freshness); JSON logs (timestamp, severity, correlation-id, message) to ELK; Grafana dashboards
    - _Requirements: 27.1, 27.2, 27.3_
  - [x] 27.2 Configure tracing and telemetry resilience
    - Zipkin spans (start, duration, service id) with correlation propagation; retry emission 3x without blocking requests
    - _Requirements: 27.4, 27.5, 27.7_
  - [ ]* 27.3 Write unit tests for log structure and telemetry resilience
    - Test required log fields and non-blocking retry on outage
    - _Requirements: 27.3, 27.7_

- [ ] 28. Scalability — Kafka and Redis integration
  - [x] 28.1 Configure Kafka topics and work submission
    - Topics: agent-tasks, agent-progress, project-state-changes, audit-events; acknowledge submission ≤2s; reject on Kafka-unavailable retaining data; re-queue failed-instance work ≤30s
    - _Requirements: 26.1, 26.5, 26.6_
  - [x] 28.2 Configure Redis shared state and load distribution
    - Redis sessions/cache/rate-limits; verify load distribution ≤20% above mean; verify new-project creation ≤5s under 100 concurrent generations
    - _Requirements: 26.2, 26.3, 26.4_
  - [ ]* 28.3 Write property test for work conservation
    - **Property 23: Work conservation**
    - **Validates: Requirements 26.1, 26.5, 26.6**

- [ ] 29. Production readiness — backup, health, and HA
  - [x] 29.1 Configure automated backups and restore
    - Backups ≤24h interval, ≥30-day retention, recorded with id+timestamp; retry 3x with Admin alert; restore ≤60 min, abort+preserve on failure with alert
    - _Requirements: 28.1, 28.2, 28.3, 28.4, 28.5, 28.6_
  - [ ] 29.2 Configure health checks and HA routing
    - K8s probes ≤30s interval / ≤10s timeout; 3 consecutive failures → unhealthy → route away ≤30s; multi-AZ; rolling deployments
    - _Requirements: 28.7, 28.8, 28.9_
  - [ ]* 29.3 Write unit tests for backup retry and health classification
    - Test 3x backup retry alerting and 3-failure unhealthy classification
    - _Requirements: 28.4, 28.8_

- [ ] 30. Kubernetes manifests and Helm charts
  - [ ] 30.1 Author K8s manifests and Helm chart
    - Deployments (resource limits, replicas, HPA, env), Service/Ingress for Gateway, ConfigMaps/Secrets, Helm chart with dev/staging/prod parameterization, namespace isolation
    - _Requirements: 28.7, 28.9, 26.2, 30_

- [ ] 31. Performance validation
  - [ ]* 31.1 Implement load tests for latency budgets
    - Non-AI ≤2s p95, chat first-token ≤5s p95, notifications ≤2s p95 at 500 users over rolling 5-min windows
    - _Requirements: 29.1, 29.2, 29.3_
  - [ ]* 31.2 Implement graceful-degradation and timeout tests
    - Progress indicator above 500 users; 10s timeout for non-AI requests terminating with error and preserved session
    - _Requirements: 29.4, 29.5_

- [ ] 32. End-to-end integration testing
  - [ ]* 32.1 Implement full-flow E2E test with provider stubs
    - register→create→describe→analyze→generate→assemble→review→approve→export; verify agent dependency order, approval gate, WebSocket notifications, audit completeness, export content and watermark behavior
    - _Requirements: 6.1, 18.3, 19.6, 22.1, 23.1, 21.4_

- [ ] 33. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP.
- Each property-based test sub-task references a specific Correctness Property from the design and the requirements clause it validates; place them next to the implementation they guard so invariant breaks surface early.
- Provider stubs (Task 7.2) are reused across all agent and E2E tests for determinism.
- Agent worker tasks (11–14) can be parallelized across developers once the common framework (11.1) exists.
- Cross-cutting tasks (26–30) are additive once core services exist.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "3.1", "6.1", "7.1", "20.1", "27.1"] },
    { "id": 3, "tasks": ["2.2", "2.3", "3.2", "3.3", "3.4", "6.2", "7.2", "20.2", "27.2"] },
    { "id": 4, "tasks": ["2.4", "2.5", "3.5", "3.6", "3.7", "3.8", "4.1", "4.2", "4.3", "6.3", "7.3", "20.3", "26.1", "26.2", "27.3", "28.1", "28.2"] },
    { "id": 5, "tasks": ["4.4", "4.5", "4.6", "6.4", "6.5", "6.6", "7.4", "7.5", "7.6", "8.1", "9.1", "20.4", "20.5", "26.3", "28.3", "29.1", "29.2"] },
    { "id": 6, "tasks": ["8.2", "8.3", "9.2", "18.1", "19.1", "29.3", "30.1"] },
    { "id": 7, "tasks": ["8.4", "9.3", "11.1", "18.2", "18.3", "19.2"] },
    { "id": 8, "tasks": ["9.4", "9.5", "9.6", "11.2", "18.4", "19.3"] },
    { "id": 9, "tasks": ["11.3", "12.1", "12.2", "12.3"] },
    { "id": 10, "tasks": ["12.4", "13.1", "13.2", "13.3"] },
    { "id": 11, "tasks": ["13.4", "14.1", "14.2", "14.3"] },
    { "id": 12, "tasks": ["14.4", "16.1"] },
    { "id": 13, "tasks": ["16.2", "16.3"] },
    { "id": 14, "tasks": ["16.4", "16.5", "16.6", "17.1"] },
    { "id": 15, "tasks": ["17.2", "21.1"] },
    { "id": 16, "tasks": ["17.3", "17.4", "21.2"] },
    { "id": 17, "tasks": ["23.1"] },
    { "id": 18, "tasks": ["23.2", "23.3"] },
    { "id": 19, "tasks": ["23.4", "24.1", "24.2", "24.3"] },
    { "id": 20, "tasks": ["25.1", "25.2"] },
    { "id": 21, "tasks": ["31.1", "31.2", "32.1"] }
  ]
}
```
