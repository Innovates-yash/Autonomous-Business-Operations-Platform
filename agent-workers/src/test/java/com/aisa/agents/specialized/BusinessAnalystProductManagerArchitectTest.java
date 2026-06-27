package com.aisa.agents.specialized;

import com.aisa.agents.framework.AgentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Business Analyst, Product Manager, and Software Architect agents.
 *
 * <p>Tests traceability (BA), story-to-use-case mapping (PM), and microservice
 * completeness (Software Architect).
 *
 * <p>Validates: Requirements 8.3, 9.2, 10.6
 */
class BusinessAnalystProductManagerArchitectTest {

    @Nested
    @DisplayName("BusinessAnalystAgent")
    class BusinessAnalystTests {

        private final BusinessAnalystAgent agent = new BusinessAnalystAgent();

        @Test
        @DisplayName("Valid output with traceability links → success")
        void validOutput_success() {
            String json = """
                    {
                      "stakeholders": [{"role": "Admin", "interest": "System management"}],
                      "valueDrivers": [{"name": "Efficiency", "description": "Faster processing", "linkedStakeholders": ["Admin"]}],
                      "constraints": [{"description": "GDPR compliance", "type": "REGULATORY"}],
                      "assumptions": [{"description": "Internet connectivity", "relatedRequirements": ["NFR-001"]}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Missing stakeholders → failure")
        void missingStakeholders_failure() {
            String json = """
                    {
                      "stakeholders": [],
                      "valueDrivers": [{"name": "Speed", "linkedStakeholders": ["User"]}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Value driver without linked stakeholders → failure")
        void valueDriverMissingLinks_failure() {
            String json = """
                    {
                      "stakeholders": [{"role": "User", "interest": "Ease of use"}],
                      "valueDrivers": [{"name": "Speed"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Missing prerequisite throws exception")
        void missingPrerequisite_throws() {
            assertThatThrownBy(() -> agent.buildPrompt(Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("REQUIREMENT_ANALYST");
        }
    }

    @Nested
    @DisplayName("ProductManagerAgent")
    class ProductManagerTests {

        private final ProductManagerAgent agent = new ProductManagerAgent();

        @Test
        @DisplayName("Valid stories and use cases with mapping → success")
        void validOutput_success() {
            String json = """
                    {
                      "userStories": [
                        {"id": "US-001", "role": "User", "feature": "search", "benefit": "find items", "priority": "HIGH"}
                      ],
                      "useCases": [
                        {"id": "UC-001", "title": "Search", "linkedStoryIds": ["US-001"]}
                      ]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Story missing priority → failure")
        void storyMissingPriority_failure() {
            String json = """
                    {
                      "userStories": [
                        {"id": "US-001", "role": "User", "feature": "search", "benefit": "find items"}
                      ],
                      "useCases": [
                        {"id": "UC-001", "title": "Search", "linkedStoryIds": ["US-001"]}
                      ]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Use case without linked stories → failure")
        void useCaseMissingLinks_failure() {
            String json = """
                    {
                      "userStories": [
                        {"id": "US-001", "role": "User", "feature": "search", "benefit": "find", "priority": "HIGH"}
                      ],
                      "useCases": [
                        {"id": "UC-001", "title": "Search"}
                      ]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }
    }

    @Nested
    @DisplayName("SoftwareArchitectAgent")
    class SoftwareArchitectTests {

        private final SoftwareArchitectAgent agent = new SoftwareArchitectAgent();

        @Test
        @DisplayName("Valid components, microservices, and interactions → success")
        void validOutput_success() {
            String json = """
                    {
                      "components": [{"name": "Auth", "responsibility": "Authentication"}],
                      "microservices": [{"name": "auth-service", "responsibilities": ["Login"], "technology": "Java 21"}],
                      "interactions": [{"from": "gateway", "to": "auth-service", "style": "SYNC"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Missing microservices → failure")
        void missingMicroservices_failure() {
            String json = """
                    {
                      "components": [{"name": "Auth", "responsibility": "Authentication"}],
                      "microservices": [],
                      "interactions": [{"from": "a", "to": "b", "style": "SYNC"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Interaction missing style → failure")
        void interactionMissingStyle_failure() {
            String json = """
                    {
                      "components": [{"name": "Auth", "responsibility": "Auth"}],
                      "microservices": [{"name": "svc", "responsibilities": ["r"], "technology": "Java"}],
                      "interactions": [{"from": "a", "to": "b"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }
    }
}
