package com.aisa.agents.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that collects all {@link SpecializedAgent} beans and provides O(1) lookup
 * by agent type name.
 *
 * <p>At startup, Spring auto-discovers all beans implementing {@link SpecializedAgent},
 * and this component indexes them by their {@link SpecializedAgent#agentType()} value.
 * The {@link AgentTaskConsumer} uses this registry to route incoming Kafka tasks to
 * the correct agent implementation.
 */
@Component
public class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, SpecializedAgent> agents;

    public AgentRegistry(List<SpecializedAgent> agentBeans) {
        this.agents = agentBeans.stream()
                .collect(Collectors.toUnmodifiableMap(
                        SpecializedAgent::agentType,
                        Function.identity()
                ));
        log.info("AgentRegistry initialized with {} agents: {}",
                agents.size(), agents.keySet());
    }

    /**
     * Look up a specialized agent by its type name.
     *
     * @param agentType the agent type (e.g. {@code "REQUIREMENT_ANALYST"})
     * @return the agent if registered, empty otherwise
     */
    public Optional<SpecializedAgent> find(String agentType) {
        return Optional.ofNullable(agents.get(agentType));
    }

    /**
     * Returns all registered agent type names.
     */
    public java.util.Set<String> registeredTypes() {
        return agents.keySet();
    }
}
