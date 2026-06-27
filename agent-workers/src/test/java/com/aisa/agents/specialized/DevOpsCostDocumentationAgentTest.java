package com.aisa.agents.specialized;

import com.aisa.agents.framework.AgentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DevOps Architect, Cost Estimation, and Documentation agents.
 *
 * <p>Tests RTO/RPO presence (DevOps), total-equals-sum and single currency (Cost),
 * and TOC/section parity + summary word bounds (Documentation).
 *
 * <p>Validates: Requirements 14.5, 15.1, 16.2, 16.3
 */
class DevOpsCostDocumentationAgentTest {

    @Nested
    @DisplayName("DevOpsArchitectAgent")
    class DevOpsArchitectTests {

        private final DevOpsArchitectAgent agent = new DevOpsArchitectAgent();

        @Test
        @DisplayName("Valid all-5-sections output with RTO/RPO → success")
        void validOutput_success() {
            String json = """
                    {
                      "cloudArchitecture": {"provider": "AWS", "regions": ["us-east-1"]},
                      "ciCd": {"tool": "GitHub Actions", "stages": [{"name": "Build", "steps": ["Compile"]}]},
                      "containerization": {"runtime": "Docker", "orchestration": "Kubernetes"},
                      "monitoring": {"metrics": {"tool": "Prometheus"}},
                      "hadr": {"rto": "15 minutes", "rpo": "5 minutes", "strategy": "Multi-AZ"}
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Missing RTO → failure")
        void missingRto_failure() {
            String json = """
                    {
                      "cloudArchitecture": {"provider": "AWS"},
                      "ciCd": {"stages": [{"name": "Build", "steps": ["Compile"]}]},
                      "containerization": {"runtime": "Docker"},
                      "monitoring": {"metrics": "Prometheus"},
                      "hadr": {"rpo": "5 minutes"}
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Missing CI/CD stages → failure")
        void missingCiCdStages_failure() {
            String json = """
                    {
                      "cloudArchitecture": {"provider": "AWS"},
                      "ciCd": {"tool": "GitHub Actions", "stages": []},
                      "containerization": {"runtime": "Docker"},
                      "monitoring": {"metrics": "Prometheus"},
                      "hadr": {"rto": "15 minutes", "rpo": "5 minutes"}
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Missing prerequisites throws exception")
        void missingPrerequisites_throws() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> agent.buildPrompt(Map.of("DATABASE_ARCHITECT", "data"))
            );
        }
    }

    @Nested
    @DisplayName("CostEstimationAgent")
    class CostEstimationTests {

        private final CostEstimationAgent agent = new CostEstimationAgent();

        @Test
        @DisplayName("Valid categories with consistent currency → success")
        void validOutput_success() {
            String json = """
                    {
                      "categories": [
                        {
                          "name": "Infrastructure",
                          "estimatedCost": {"min": 2000, "max": 5000, "currency": "USD"}
                        },
                        {
                          "name": "Development",
                          "estimatedCost": {"min": 50000, "max": 100000, "currency": "USD"}
                        }
                      ],
                      "totalEstimate": {"min": 52000, "max": 105000, "currency": "USD"},
                      "assumptions": [{"description": "10k MAU", "impact": "Sizing"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Currency mismatch between category and total → failure")
        void currencyMismatch_failure() {
            String json = """
                    {
                      "categories": [
                        {
                          "name": "Infrastructure",
                          "estimatedCost": {"min": 2000, "max": 5000, "currency": "EUR"}
                        }
                      ],
                      "totalEstimate": {"min": 2000, "max": 5000, "currency": "USD"},
                      "assumptions": [{"description": "Test"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
            assertThat(((AgentResult.Failure) result).errorMessage()).contains("Currency mismatch");
        }

        @Test
        @DisplayName("Missing total estimate → failure")
        void missingTotal_failure() {
            String json = """
                    {
                      "categories": [
                        {"name": "Infra", "estimatedCost": {"min": 100, "max": 200, "currency": "USD"}}
                      ],
                      "assumptions": [{"description": "Test"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Empty assumptions → failure")
        void emptyAssumptions_failure() {
            String json = """
                    {
                      "categories": [
                        {"name": "Infra", "estimatedCost": {"min": 100, "max": 200, "currency": "USD"}}
                      ],
                      "totalEstimate": {"min": 100, "max": 200, "currency": "USD"},
                      "assumptions": []
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }
    }

    @Nested
    @DisplayName("DocumentationAgent")
    class DocumentationTests {

        private final DocumentationAgent agent = new DocumentationAgent();

        @Test
        @DisplayName("Valid document with matching TOC and 100+ word summary → success")
        void validOutput_success() {
            // Generate a summary with exactly ~120 words
            String summary = "This document provides a comprehensive overview of the architecture design " +
                    "for the proposed system. The platform is designed to handle large-scale operations " +
                    "with high availability and disaster recovery capabilities. Key decisions include " +
                    "the use of microservices architecture with event-driven communication patterns, " +
                    "a relational database with Redis caching layer, and comprehensive security measures " +
                    "including JWT authentication and RBAC authorization. The CI/CD pipeline ensures " +
                    "automated testing and deployment. Cost estimates indicate a moderate initial " +
                    "investment with predictable operational costs. The implementation roadmap outlines " +
                    "a phased approach starting with core services and progressively adding features. " +
                    "This architecture balances performance, security, and cost-effectiveness for " +
                    "the anticipated workload and user base of the application system.";

            String json = """
                    {
                      "executiveSummary": "%s",
                      "tableOfContents": ["Overview", "Architecture"],
                      "sections": [
                        {"title": "Overview", "content": "System overview content here."},
                        {"title": "Architecture", "content": "Architecture details here."}
                      ]
                    }
                    """.formatted(summary);

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Executive summary too short (< 100 words) → failure")
        void summaryTooShort_failure() {
            String json = """
                    {
                      "executiveSummary": "This is a very short summary.",
                      "tableOfContents": ["Overview"],
                      "sections": [{"title": "Overview", "content": "Details"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
            assertThat(((AgentResult.Failure) result).errorMessage()).contains("too short");
        }

        @Test
        @DisplayName("TOC does not match sections → failure")
        void tocMismatch_failure() {
            String longSummary = "word ".repeat(150);
            String json = """
                    {
                      "executiveSummary": "%s",
                      "tableOfContents": ["Overview", "Missing Section"],
                      "sections": [
                        {"title": "Overview", "content": "Content"},
                        {"title": "Different Section", "content": "Content"}
                      ]
                    }
                    """.formatted(longSummary);

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
            assertThat(((AgentResult.Failure) result).errorMessage()).contains("does not match");
        }

        @Test
        @DisplayName("Section without content → failure")
        void sectionMissingContent_failure() {
            String longSummary = "word ".repeat(150);
            String json = """
                    {
                      "executiveSummary": "%s",
                      "tableOfContents": ["Overview"],
                      "sections": [{"title": "Overview"}]
                    }
                    """.formatted(longSummary);

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }
    }
}
