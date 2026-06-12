package com.aisa.orchestrator.config;

import com.aisa.orchestrator.domain.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link AgentDependencyDag}.
 * Validates: Requirements 6.1, 6.2 — dependency ordering and DAG integrity.
 */
class AgentDependencyDagTest {

    private AgentDependencyDag dag;

    @BeforeEach
    void setUp() {
        dag = new AgentDependencyDag();
    }

    @Test
    @DisplayName("All ten agent types are present in the DAG")
    void allAgentsReachable() {
        Map<AgentType, Set<AgentType>> all = dag.getAllDependencies();
        for (AgentType agent : AgentType.values()) {
            assertTrue(all.containsKey(agent),
                    "Agent " + agent + " must be present in the dependency map");
        }
        assertEquals(AgentType.values().length, all.size());
    }

    @Test
    @DisplayName("DAG has no cycles — topological sort completes visiting all agents")
    void noCycles() {
        // Kahn's algorithm: if the topological sort visits all nodes, there are no cycles.
        Map<AgentType, Set<AgentType>> allDeps = dag.getAllDependencies();
        EnumSet<AgentType> visited = EnumSet.noneOf(AgentType.class);
        Deque<AgentType> queue = new ArrayDeque<>();

        // Seed with agents that have no dependencies (in-degree 0).
        for (AgentType agent : AgentType.values()) {
            if (allDeps.get(agent).isEmpty()) {
                queue.add(agent);
            }
        }

        while (!queue.isEmpty()) {
            AgentType current = queue.poll();
            visited.add(current);
            // Find agents whose dependencies are now all visited.
            for (AgentType candidate : AgentType.values()) {
                if (!visited.contains(candidate) && !queue.contains(candidate)) {
                    if (visited.containsAll(allDeps.get(candidate))) {
                        queue.add(candidate);
                    }
                }
            }
        }

        assertEquals(EnumSet.allOf(AgentType.class), visited,
                "Topological sort must visit all agents — a cycle would prevent this");
    }

    @Test
    @DisplayName("REQUIREMENT_ANALYST has no dependencies (entry point)")
    void requirementAnalystIsRoot() {
        Set<AgentType> deps = dag.getDependencies(AgentType.REQUIREMENT_ANALYST);
        assertNotNull(deps);
        assertTrue(deps.isEmpty(), "REQUIREMENT_ANALYST should have no prerequisites");
    }

    @Test
    @DisplayName("DOCUMENTATION is a terminal node")
    void documentationIsTerminal() {
        assertTrue(dag.isTerminal(AgentType.DOCUMENTATION),
                "DOCUMENTATION should be a terminal node (no dependents)");
    }

    @Test
    @DisplayName("Non-terminal agents are not marked as terminal")
    void nonTerminalAgentsAreNotTerminal() {
        // SOFTWARE_ARCHITECT has three dependents (DB, Security, API architects)
        assertFalse(dag.isTerminal(AgentType.SOFTWARE_ARCHITECT));
        assertFalse(dag.isTerminal(AgentType.REQUIREMENT_ANALYST));
        assertFalse(dag.isTerminal(AgentType.BUSINESS_ANALYST));
    }

    @Test
    @DisplayName("getReadyAgents with empty completed set returns only root agents")
    void readyAgentsFromEmpty() {
        Set<AgentType> ready = dag.getReadyAgents(EnumSet.noneOf(AgentType.class));
        assertEquals(EnumSet.of(AgentType.REQUIREMENT_ANALYST), ready,
                "Only REQUIREMENT_ANALYST has no dependencies");
    }

    @Test
    @DisplayName("getReadyAgents after REQUIREMENT_ANALYST completes returns BUSINESS_ANALYST")
    void readyAgentsAfterFirstCompletes() {
        Set<AgentType> completed = EnumSet.of(AgentType.REQUIREMENT_ANALYST);
        Set<AgentType> ready = dag.getReadyAgents(completed);
        assertEquals(EnumSet.of(AgentType.BUSINESS_ANALYST), ready);
    }

    @Test
    @DisplayName("getReadyAgents after SOFTWARE_ARCHITECT completes returns three parallel agents")
    void readyAgentsAfterSoftwareArchitect() {
        Set<AgentType> completed = EnumSet.of(
                AgentType.REQUIREMENT_ANALYST,
                AgentType.BUSINESS_ANALYST,
                AgentType.PRODUCT_MANAGER,
                AgentType.SOFTWARE_ARCHITECT);
        Set<AgentType> ready = dag.getReadyAgents(completed);
        assertEquals(EnumSet.of(AgentType.DATABASE_ARCHITECT, AgentType.SECURITY_ARCHITECT, AgentType.API_ARCHITECT),
                ready, "DB, Security, and API architects should be ready in parallel (Req 6.10)");
    }

    @Test
    @DisplayName("getReadyAgents after mid-tier agents complete returns DevOps and Cost parallel")
    void readyAgentsDevOpsAndCostParallel() {
        Set<AgentType> completed = EnumSet.of(
                AgentType.REQUIREMENT_ANALYST,
                AgentType.BUSINESS_ANALYST,
                AgentType.PRODUCT_MANAGER,
                AgentType.SOFTWARE_ARCHITECT,
                AgentType.DATABASE_ARCHITECT,
                AgentType.SECURITY_ARCHITECT,
                AgentType.API_ARCHITECT);
        Set<AgentType> ready = dag.getReadyAgents(completed);
        assertEquals(EnumSet.of(AgentType.DEVOPS_ARCHITECT, AgentType.COST_ESTIMATION),
                ready, "DevOps and Cost Estimation should be ready in parallel (Req 6.10)");
    }

    @Test
    @DisplayName("getReadyAgents with all completed returns empty set")
    void readyAgentsAllCompleted() {
        Set<AgentType> completed = EnumSet.allOf(AgentType.class);
        Set<AgentType> ready = dag.getReadyAgents(completed);
        assertTrue(ready.isEmpty(), "No agents should be ready when all are completed");
    }

    @Test
    @DisplayName("Dependency structure matches design document")
    void dependencyStructureMatchesDesign() {
        assertEquals(EnumSet.noneOf(AgentType.class),
                dag.getDependencies(AgentType.REQUIREMENT_ANALYST));
        assertEquals(EnumSet.of(AgentType.REQUIREMENT_ANALYST),
                dag.getDependencies(AgentType.BUSINESS_ANALYST));
        assertEquals(EnumSet.of(AgentType.BUSINESS_ANALYST),
                dag.getDependencies(AgentType.PRODUCT_MANAGER));
        assertEquals(EnumSet.of(AgentType.PRODUCT_MANAGER),
                dag.getDependencies(AgentType.SOFTWARE_ARCHITECT));
        assertEquals(EnumSet.of(AgentType.SOFTWARE_ARCHITECT),
                dag.getDependencies(AgentType.DATABASE_ARCHITECT));
        assertEquals(EnumSet.of(AgentType.SOFTWARE_ARCHITECT),
                dag.getDependencies(AgentType.SECURITY_ARCHITECT));
        assertEquals(EnumSet.of(AgentType.SOFTWARE_ARCHITECT),
                dag.getDependencies(AgentType.API_ARCHITECT));
        assertEquals(EnumSet.of(AgentType.DATABASE_ARCHITECT, AgentType.SECURITY_ARCHITECT, AgentType.API_ARCHITECT),
                dag.getDependencies(AgentType.DEVOPS_ARCHITECT));
        assertEquals(EnumSet.of(AgentType.DATABASE_ARCHITECT, AgentType.SECURITY_ARCHITECT, AgentType.API_ARCHITECT),
                dag.getDependencies(AgentType.COST_ESTIMATION));
        assertEquals(EnumSet.of(AgentType.DEVOPS_ARCHITECT, AgentType.COST_ESTIMATION),
                dag.getDependencies(AgentType.DOCUMENTATION));
    }

    @Test
    @DisplayName("Transitive dependents of REQUIREMENT_ANALYST includes all downstream agents")
    void transitiveDependentsOfRoot() {
        Set<AgentType> transitive = dag.getTransitiveDependents(AgentType.REQUIREMENT_ANALYST);
        // Everything except REQUIREMENT_ANALYST itself should be a transitive dependent.
        EnumSet<AgentType> expected = EnumSet.allOf(AgentType.class);
        expected.remove(AgentType.REQUIREMENT_ANALYST);
        assertEquals(expected, transitive);
    }

    @Test
    @DisplayName("Transitive dependents of DOCUMENTATION is empty (terminal)")
    void transitiveDependentsOfTerminal() {
        Set<AgentType> transitive = dag.getTransitiveDependents(AgentType.DOCUMENTATION);
        assertTrue(transitive.isEmpty());
    }

    @Test
    @DisplayName("isReady returns true when all dependencies are satisfied")
    void isReadyWhenDependenciesMet() {
        assertTrue(dag.isReady(AgentType.REQUIREMENT_ANALYST, EnumSet.noneOf(AgentType.class)));
        assertTrue(dag.isReady(AgentType.BUSINESS_ANALYST, EnumSet.of(AgentType.REQUIREMENT_ANALYST)));
        assertTrue(dag.isReady(AgentType.DEVOPS_ARCHITECT,
                EnumSet.of(AgentType.DATABASE_ARCHITECT, AgentType.SECURITY_ARCHITECT, AgentType.API_ARCHITECT)));
    }

    @Test
    @DisplayName("isReady returns false when dependencies are not satisfied")
    void isReadyWhenDependenciesNotMet() {
        assertFalse(dag.isReady(AgentType.BUSINESS_ANALYST, EnumSet.noneOf(AgentType.class)));
        assertFalse(dag.isReady(AgentType.DEVOPS_ARCHITECT,
                EnumSet.of(AgentType.DATABASE_ARCHITECT, AgentType.SECURITY_ARCHITECT)));
    }
}
