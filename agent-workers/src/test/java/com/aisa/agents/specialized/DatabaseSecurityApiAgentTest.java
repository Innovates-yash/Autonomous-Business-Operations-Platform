package com.aisa.agents.specialized;

import com.aisa.agents.framework.AgentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Database Architect, Security Architect, and API Architect agents.
 *
 * <p>Tests cardinality enumeration (DB), encryption sections (Security),
 * and per-boundary auth requirements (API).
 *
 * <p>Validates: Requirements 11.2, 12.3, 12.4, 13.4
 */
class DatabaseSecurityApiAgentTest {

    @Nested
    @DisplayName("DatabaseArchitectAgent")
    class DatabaseArchitectTests {

        private final DatabaseArchitectAgent agent = new DatabaseArchitectAgent();

        @Test
        @DisplayName("Valid entities, relationships with cardinality, and caching → success")
        void validOutput_success() {
            String json = """
                    {
                      "entities": [
                        {"name": "User", "attributes": [{"name": "id", "type": "UUID"}], "keys": {"primary": "id"}}
                      ],
                      "relationships": [
                        {"from": "User", "to": "Project", "cardinality": "ONE_TO_MANY", "type": "OWNERSHIP"}
                      ],
                      "cachingStrategy": {"provider": "Redis", "patterns": []}
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Relationship missing cardinality → failure")
        void missingCardinality_failure() {
            String json = """
                    {
                      "entities": [{"name": "User", "attributes": [{"name": "id"}]}],
                      "relationships": [{"from": "User", "to": "Project"}],
                      "cachingStrategy": {"provider": "Redis"}
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Missing caching strategy → failure")
        void missingCaching_failure() {
            String json = """
                    {
                      "entities": [{"name": "User", "attributes": [{"name": "id"}]}],
                      "relationships": [{"from": "User", "to": "Project", "cardinality": "ONE_TO_MANY"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Empty entities array → failure")
        void emptyEntities_failure() {
            String json = """
                    {
                      "entities": [],
                      "relationships": [{"from": "A", "to": "B", "cardinality": "ONE_TO_ONE"}],
                      "cachingStrategy": {"provider": "Redis"}
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }
    }

    @Nested
    @DisplayName("SecurityArchitectAgent")
    class SecurityArchitectTests {

        private final SecurityArchitectAgent agent = new SecurityArchitectAgent();

        @Test
        @DisplayName("Valid auth/authz/dataProtection/threats → success")
        void validOutput_success() {
            String json = """
                    {
                      "authentication": {"mechanisms": ["JWT"]},
                      "authorization": {"model": "RBAC", "roles": []},
                      "dataProtection": {
                        "atRest": {"encryption": "AES-256"},
                        "inTransit": {"protocol": "TLS 1.3"}
                      },
                      "threats": [{"threat": "SQL Injection", "mitigation": "Parameterized queries"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Missing data protection at-rest encryption → failure")
        void missingAtRest_failure() {
            String json = """
                    {
                      "authentication": {"mechanisms": ["JWT"]},
                      "authorization": {"model": "RBAC"},
                      "dataProtection": {
                        "inTransit": {"protocol": "TLS 1.3"}
                      },
                      "threats": [{"threat": "XSS", "mitigation": "Encoding"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Missing authorization model → failure")
        void missingAuthModel_failure() {
            String json = """
                    {
                      "authentication": {"mechanisms": ["JWT"]},
                      "authorization": {"roles": ["ADMIN"]},
                      "dataProtection": {"atRest": "AES", "inTransit": "TLS"},
                      "threats": [{"threat": "XSS", "mitigation": "Encoding"}]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Empty threats array → failure")
        void emptyThreats_failure() {
            String json = """
                    {
                      "authentication": {"mechanisms": ["JWT"]},
                      "authorization": {"model": "RBAC"},
                      "dataProtection": {"atRest": "AES", "inTransit": "TLS"},
                      "threats": []
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }
    }

    @Nested
    @DisplayName("ApiArchitectAgent")
    class ApiArchitectTests {

        private final ApiArchitectAgent agent = new ApiArchitectAgent();

        @Test
        @DisplayName("Valid boundaries with operations and auth → success")
        void validOutput_success() {
            String json = """
                    {
                      "serviceBoundaries": [
                        {
                          "name": "User API",
                          "authentication": "JWT",
                          "authorization": "RBAC",
                          "operations": [
                            {"method": "POST", "path": "/api/v1/users", "summary": "Create user"}
                          ]
                        }
                      ]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Success.class);
        }

        @Test
        @DisplayName("Boundary without auth fields → failure")
        void missingAuth_failure() {
            String json = """
                    {
                      "serviceBoundaries": [
                        {
                          "name": "User API",
                          "operations": [{"method": "GET", "path": "/api/v1/users"}]
                        }
                      ]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }

        @Test
        @DisplayName("Boundary with empty operations → failure")
        void emptyOperations_failure() {
            String json = """
                    {
                      "serviceBoundaries": [
                        {
                          "name": "User API",
                          "authentication": "JWT",
                          "authorization": "RBAC",
                          "operations": []
                        }
                      ]
                    }
                    """;

            AgentResult result = agent.processResponse(json);
            assertThat(result).isInstanceOf(AgentResult.Failure.class);
        }
    }
}
