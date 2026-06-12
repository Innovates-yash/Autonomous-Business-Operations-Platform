package com.aisa.orchestrator.config;

import com.aisa.orchestrator.domain.AgentType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Encodes the ten-agent dependency DAG defined in the design document
 * (Requirements 6.1, 6.10).
 *
 * <p>Each agent declares the set of prerequisite agents whose valid outputs
 * must be available before it can be invoked. Agents with no mutual dependency
 * may run concurrently (Requirement 6.10).
 *
 * <pre>
 * REQUIREMENT_ANALYST  → (no dependencies)
 * BUSINESS_ANALYST     → REQUIREMENT_ANALYST
 * PRODUCT_MANAGER      → BUSINESS_ANALYST
 * SOFTWARE_ARCHITECT   → PRODUCT_MANAGER
 * DATABASE_ARCHITECT   → SOFTWARE_ARCHITECT
 * SECURITY_ARCHITECT   → SOFTWARE_ARCHITECT
 * API_ARCHITECT        → SOFTWARE_ARCHITECT
 * DEVOPS_ARCHITECT     → DATABASE_ARCHITECT, SECURITY_ARCHITECT, API_ARCHITECT
 * COST_ESTIMATION      → DATABASE_ARCHITECT, SECURITY_ARCHITECT, API_ARCHITECT
 * DOCUMENTATION        → DEVOPS_ARCHITECT, COST_ESTIMATION
 * </pre>
 */
@Component
public class AgentDependencyDag {

    private final Map<AgentType, Set<AgentType>> dependencies;

    public AgentDependencyDag() {
        Map<AgentType, Set<AgentType>> dag = new EnumMap<>(AgentType.class);

        dag.put(AgentType.REQUIREMENT_ANALYST, EnumSet.noneOf(AgentType.class));
        dag.put(AgentType.BUSINESS_ANALYST, EnumSet.of(AgentType.REQUIREMENT_ANALYST));
        dag.put(AgentType.PRODUCT_MANAGER, EnumSet.of(AgentType.BUSINESS_ANALYST));
        dag.put(AgentType.SOFTWARE_ARCHITECT, EnumSet.of(AgentType.PRODUCT_MANAGER));
        dag.put(AgentType.DATABASE_ARCHITECT, EnumSet.of(AgentType.SOFTWARE_ARCHITECT));
        dag.put(AgentType.SECURITY_ARCHITECT, EnumSet.of(AgentType.SOFTWARE_ARCHITECT));
        dag.put(AgentType.API_ARCHITECT, EnumSet.of(AgentType.SOFTWARE_ARCHITECT));
        dag.put(AgentType.DEVOPS_ARCHITECT, EnumSet.of(
                AgentType.DATABASE_ARCHITECT, AgentType.SECURITY_ARCHITECT, AgentType.API_ARCHITECT));
        dag.put(AgentType.COST_ESTIMATION, EnumSet.of(
                AgentType.DATABASE_ARCHITECT, AgentType.SECURITY_ARCHITECT, AgentType.API_ARCHITECT));
        dag.put(AgentType.DOCUMENTATION, EnumSet.of(
                AgentType.DEVOPS_ARCHITECT, AgentType.COST_ESTIMATION));

        this.dependencies = Collections.unmodifiableMap(dag);
    }

    /**
     * Returns the set of direct prerequisite agents for the given agent type.
     *
     * @param agentType the agent whose dependencies are requested
     * @return an unmodifiable set of prerequisite agent types (empty if the agent has
     *         no dependencies)
     */
    public Set<AgentType> getDependencies(AgentType agentType) {
        return Collections.unmodifiableSet(
                dependencies.getOrDefault(agentType, EnumSet.noneOf(AgentType.class)));
    }

    /**
     * Returns the full dependency map for all agent types.
     *
     * @return an unmodifiable map of each agent type to its set of direct prerequisites
     */
    public Map<AgentType, Set<AgentType>> getAllDependencies() {
        return dependencies;
    }

    /**
     * Returns the set of agents that directly depend on the given agent.
     *
     * @param agentType the agent whose dependents are requested
     * @return an unmodifiable set of agents that directly depend on the given agent
     */
    public Set<AgentType> getDependents(AgentType agentType) {
        EnumSet<AgentType> dependents = EnumSet.noneOf(AgentType.class);
        for (Map.Entry<AgentType, Set<AgentType>> entry : dependencies.entrySet()) {
            if (entry.getValue().contains(agentType)) {
                dependents.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(dependents);
    }

    /**
     * Returns the set of agents that transitively depend on the given agent.
     * Used for halting dependent steps on failure (Requirement 6.6).
     *
     * @param agentType the failed agent
     * @return all agents that directly or transitively depend on the given agent
     */
    public Set<AgentType> getTransitiveDependents(AgentType agentType) {
        EnumSet<AgentType> result = EnumSet.noneOf(AgentType.class);
        collectTransitiveDependents(agentType, result);
        return Collections.unmodifiableSet(result);
    }

    private void collectTransitiveDependents(AgentType agentType, EnumSet<AgentType> accumulator) {
        Set<AgentType> directDependents = getDependents(agentType);
        for (AgentType dependent : directDependents) {
            if (accumulator.add(dependent)) {
                collectTransitiveDependents(dependent, accumulator);
            }
        }
    }

    /**
     * Determines whether an agent is ready to execute, given the set of agents
     * that have already completed successfully.
     *
     * @param agentType the agent to check readiness for
     * @param completedAgents the set of agents that have produced valid output
     * @return {@code true} if all prerequisites of the agent are in the completed set
     */
    public boolean isReady(AgentType agentType, Set<AgentType> completedAgents) {
        return completedAgents.containsAll(getDependencies(agentType));
    }

    /**
     * Returns all agents that are ready to execute given the set of already-completed
     * agents. An agent is ready if all of its prerequisites are in the completed set and
     * the agent itself has not yet completed (Requirement 6.10).
     *
     * @param completedAgents the set of agents that have already produced valid output
     * @return an unmodifiable set of agents whose dependencies are all satisfied and
     *         that have not yet completed
     */
    public Set<AgentType> getReadyAgents(Set<AgentType> completedAgents) {
        EnumSet<AgentType> ready = EnumSet.noneOf(AgentType.class);
        for (AgentType agent : AgentType.values()) {
            if (!completedAgents.contains(agent) && isReady(agent, completedAgents)) {
                ready.add(agent);
            }
        }
        return Collections.unmodifiableSet(ready);
    }

    /**
     * Returns {@code true} if the given agent is a terminal node in the DAG,
     * meaning no other agent depends on it.
     *
     * @param agentType the agent to check
     * @return {@code true} if no other agent lists this agent as a prerequisite
     */
    public boolean isTerminal(AgentType agentType) {
        for (Map.Entry<AgentType, Set<AgentType>> entry : dependencies.entrySet()) {
            if (entry.getValue().contains(agentType)) {
                return false;
            }
        }
        return true;
    }
}
