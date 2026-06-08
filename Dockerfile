# Parameterized multi-stage build for any service module.
# Usage: docker build --build-arg MODULE=auth-service -t aisa/auth-service .
#
# Stage 1 builds the whole reactor (so the shared commons module is available),
# then stage 2 runs only the selected module's executable jar on a slim JRE.

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first
COPY pom.xml .
COPY commons/pom.xml commons/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY project-service/pom.xml project-service/
COPY orchestrator-service/pom.xml orchestrator-service/
COPY ai-provider-gateway/pom.xml ai-provider-gateway/
COPY ai-chat-service/pom.xml ai-chat-service/
COPY blueprint-service/pom.xml blueprint-service/
COPY export-service/pom.xml export-service/
COPY notification-service/pom.xml notification-service/
COPY audit-service/pom.xml audit-service/
COPY agent-workers/pom.xml agent-workers/
RUN mvn -B -q dependency:go-offline || true

# Build sources
COPY . .
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
