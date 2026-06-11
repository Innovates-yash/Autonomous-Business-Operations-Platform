package com.aisa.orchestrator.domain;

/**
 * Enumerates the ten specialized AI agents that collaborate to produce a complete
 * architecture Blueprint. The dependency ordering among these agents is defined in
 * {@link com.aisa.orchestrator.config.AgentDependencyDag}.
 *
 * <p>Requirements 6.1, 6.10 — agents are invoked in dependency order; agents with
 * no mutual dependency may run concurrently.
 */
public enum AgentType {

    /** Extracts functional/non-functional requirements from the Idea (Requirement 7). */
    REQUIREMENT_ANALYST,

    /** Analyzes business context, stakeholders, and value drivers (Requirement 8). */
    BUSINESS_ANALYST,

    /** Produces user stories, use cases, and priorities (Requirement 9). */
    PRODUCT_MANAGER,

    /** Decomposes the solution into microservices/components (Requirement 10). */
    SOFTWARE_ARCHITECT,

    /** Produces ER model and database design (Requirement 11). */
    DATABASE_ARCHITECT,

    /** Produces security design (Requirement 12). */
    SECURITY_ARCHITECT,

    /** Produces API design and service boundaries (Requirement 13). */
    API_ARCHITECT,

    /** Produces DevOps/cloud architecture (Requirement 14). */
    DEVOPS_ARCHITECT,

    /** Produces cost estimates (Requirement 15). */
    COST_ESTIMATION,

    /** Compiles all artifacts into human-readable documentation (Requirement 16). */
    DOCUMENTATION
}
